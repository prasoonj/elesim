package com.lightbend.akka.sample
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.collection.mutable.HashSet
import akka.actor.Timers

object ElevatorActor {
  def props(name: String, elevatorRange: (Int, Int), printerActor: ActorRef): Props = 
    Props(new ElevatorActor(name, elevatorRange, printerActor))
    
  sealed trait Msg
  case object WhereAreYou
  case object MoveOneFloor
  case object GoInLimbo
//  case class ElevatorState(currentFloor: Int, direction: String)
  case class AddStoppage(from: Int, to: Int)
  case class CanStop(from: Int, to: Int, direction: String)
//  case class ExistingStop(floor: Int, direction: String)
  case class Status(currentFloor: Int, direction: String, stops: List[Int])
  case object ReportStatus
  
  import IOActor._
  case object FloorRequest extends RequestResponseCode
  case object EmergencyStop extends RequestResponseCode
  
  // Messages for elevator timers
  private case object TickKey
  private case object RestartTick //When starting from a floor
  private case object Tick //Stop at a floor
  private case object BoardingTickKey
}

class ElevatorActor(name: String, elevatorRange: (Int, Int), ioActor: ActorRef) extends Actor  
with Timers with ActorLogging {
  import ElevatorActor._
  import Controller._
  import IOActor._
  
  import scala.concurrent.duration._

  var greeting = ""
  var currentFloor = elevatorRange._1
  var currentDirection = "LIMBO"
  var state = State(0, Limbo, Limbo) 
  var stops: scala.collection.mutable.Set[Int] = HashSet.empty
  var pickUps: scala.collection.mutable.Set[PickUp] = HashSet.empty
  var pickUpsDirection: String = "LIMBO"
  
  case class State(currentFloor: Int
      , currentDir: Direction
      , pickUpsDir: Direction)
   
  def status(): Status = Status(currentFloor, currentDirection, stops.toList)
  
  def inRange(from: Int, to: Int, state: State) = state.currentDir match {
    case Up => (from > state.currentFloor)
    case Down => (from < state.currentFloor)
    case Limbo => true
  }
  
  def validPickup(pickUp: PickUp, state: State): Boolean = if(
      (pickUp.direction == state.currentDir)
      && (pickUp.direction == state.pickUpsDir)
      && inRange(pickUp.from, pickUp.to, state)) true
  else if(state.currentDir == Limbo) true
  else false
  
  def getDirection(destination: Int): Direction = 
    if(destination < state.currentFloor) Down
    else if(destination > state.currentFloor) Up
    else Limbo
  

  def receive = {
    case RequestPickup(pickUp) => {
      if((pickUp.direction == state.currentDir)
          && (pickUp.direction == state.pickUpsDir)
          && stops.contains(pickUp.from)) {
        context.sender() ! ExistingStop(pickUp)
        pickUps.add(pickUp)
      }
      if(validPickup(pickUp, state)) context.sender() ! CanPickup(pickUp)
    }
    case AddPickup(pickUp) => {
      pickUps.add(pickUp)
      state.currentDir match {
        case Limbo =>
          // Add pickUp.from to stops
          // When pickUp.from floor is reached =>
          // 1. Add pickUp.to to stops
          // 2. Remove pickUp from pickUps
          pickUps.foreach { p => {
            stops.add(p.from)
            state = State(state.currentFloor, getDirection(p.from), p.direction)
            }
          }
          // Start motion!
          self ! RestartTick
        case _ =>
          pickUps.foreach { p => stops.add(p.from) }
      }
    }
    case GoInLimbo =>
      ioActor ! MessageFrom(s"Welcome to floor: ${state.currentFloor}")
      timers.cancelAll()
      state = State(state.currentFloor, Limbo, Limbo)
    case RestartTick =>
      timers.startPeriodicTimer(TickKey, Tick, 2.second)
    case Tick => state.currentDir match {
      case Up if(state.currentFloor < elevatorRange._2) => 
        state = State(state.currentFloor+1, state.currentDir, state.pickUpsDir)
        ioActor ! MessageFrom(s"I'm at ${state.currentFloor}")
        if(stops.isEmpty) self ! GoInLimbo
        if(stops.contains(state.currentFloor)) {
          stops.remove(state.currentFloor)
          timers.cancel(TickKey)
          //Check for pickups at this floor
          val pickUpsHere = pickUps.filter{ p => (p.from == state.currentFloor) }
          pickUpsHere
          .map { p =>
            stops.remove(p.from)
            stops.add(p.to)
            state = State(state.currentFloor, p.direction, p.direction)
          }
          pickUpsHere.foreach(p => pickUps.remove(p))
        }
        if(!stops.isEmpty) timers.startSingleTimer(BoardingTickKey, RestartTick, 5.second)
        else (self ! GoInLimbo)
      case Down if(state.currentFloor > elevatorRange._1) =>
        state = State(state.currentFloor-1, state.currentDir, state.pickUpsDir)
        ioActor ! MessageFrom(s"I'm at ${state.currentFloor}")
        if(stops.isEmpty) self ! GoInLimbo
        if(stops.contains(state.currentFloor)) {
          stops.remove(state.currentFloor)
          timers.cancel(TickKey)
          val pickUpsHere = pickUps.filter { p => (p.from == state.currentFloor) }
          pickUpsHere
          .map { p =>
            stops.remove(p.from)
            stops.add(p.to)
            state = State(state.currentFloor, p.direction, p.direction)
          }
          pickUpsHere.foreach(p => pickUps.remove(p))
        }
        if(!stops.isEmpty) timers.startSingleTimer(BoardingTickKey, RestartTick, 5.second)
        else (self ! GoInLimbo)
      case _ => self ! GoInLimbo
    }
    case MoveOneFloor => 
      if(!stops.isEmpty) {
        if(currentDirection.equals("UP") && currentFloor < elevatorRange._2) currentFloor += 1
        else if(currentDirection.equals("DOWN") && currentFloor > elevatorRange._1) currentFloor -= 1
      }
      // Go back to Tick!
      context.self ! Tick
    case ReportStatus => ioActor ! Message(s"#$name: @${status.currentFloor}, going ${status.direction}: ${stops.toList}")
  }
  
  def moveOneFloor = if(!stops.isEmpty) {
        if(currentDirection.equals("UP") && currentFloor < elevatorRange._2) currentFloor += 1
        else if(currentDirection.equals("DOWN") && currentFloor > elevatorRange._1) currentFloor -= 1
      }
}