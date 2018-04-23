package com.snortingcode
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
  case class CanStop(from: Int, to: Int, direction: String)
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

  var VERBOSE = false
  var MANUAL_MODE = false
  
  var state = State(0, Limbo, Limbo) 
  var stops: scala.collection.mutable.Set[Int] = HashSet.empty
  var pickUps: scala.collection.mutable.Set[PickUp] = HashSet.empty
  
  case class State(currentFloor: Int
      , currentDir: Direction
      , pickUpsDir: Direction)
   
  def status(): Status = { 
    val direction = state.currentDir match {
      case Limbo => "LIMBO"
      case Up => "UP"
      case Down => "DOWN"
    }
    Status(state.currentFloor, direction, stops.toList)
  }
  
  def inRange(from: Int, to: Int, state: State) = state.currentDir match {
    case Up => (from > state.currentFloor)
    case Down => (from < state.currentFloor)
    case Limbo => true
  }
  
  def validPickup(pickUp: PickUp, state: State): Boolean = if(
      (pickUp.direction == state.currentDir)
      && (pickUp.direction == state.pickUpsDir)
      && inRange(pickUp.from, pickUp.to, state)) true
  else if(state.currentDir == Limbo) {
    /*
     * Just enough time to give priority to the
     * elevators that are already in operation in the 
     * right direction and are in range.
     */
    Thread.sleep(500)
    true
  }
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
      ioActor ! MessageFrom(s"Elevator $name", s"Welcome to floor: ${state.currentFloor}")
      timers.cancelAll()
      state = State(state.currentFloor, Limbo, Limbo)
    case StartManualMode => MANUAL_MODE = true
    case DisableManualMode => MANUAL_MODE = false
    case NextStep => {
      self ! Tick
      context.parent ! DisplayDash
    }
    case RestartTick =>
      if(!MANUAL_MODE) {
        timers.startPeriodicTimer(TickKey, Tick, 2.second)
        context.parent ! DisplayDash
      }
    case Tick => state.currentDir match {
      case Up if(state.currentFloor < elevatorRange._2) => 
        state = State(state.currentFloor+1, state.currentDir, state.pickUpsDir)
        if(VERBOSE) ioActor ! MessageFrom(s"Elevator $name", s"I'm at ${state.currentFloor}")
        context.parent ! DisplayDash
        if(stops.isEmpty) self ! GoInLimbo
        if(stops.contains(state.currentFloor)) {
          stops.remove(state.currentFloor)
          timers.cancel(TickKey)
          processPickups()
        }
        if(!stops.isEmpty) timers.startSingleTimer(BoardingTickKey, RestartTick, 5.second)
        else (self ! GoInLimbo)
      case Down if(state.currentFloor > elevatorRange._1) =>
        state = State(state.currentFloor-1, state.currentDir, state.pickUpsDir)
        if(VERBOSE) ioActor ! MessageFrom(s"Elevator $name", s"I'm at ${state.currentFloor}")
        context.parent ! DisplayDash
        if(stops.isEmpty) self ! GoInLimbo
        if(stops.contains(state.currentFloor)) {
          stops.remove(state.currentFloor)
          timers.cancel(TickKey)
          processPickups()
        }
        if(!stops.isEmpty) timers.startSingleTimer(BoardingTickKey, RestartTick, 5.second)
        else (self ! GoInLimbo)
      case _ => self ! GoInLimbo
    }
    case ReportStatus => ioActor ! Message(s"Elevator#$name: @${status.currentFloor}, going ${status.direction}: ${stops.toList}".replace("List", "Stops"))
    case VerboseOutput => VERBOSE = true
  }
  
  def processPickups() = {
    val pickUpsHere = pickUps.filter{ p => (p.from == state.currentFloor) }
          pickUpsHere
          .map { p =>
            stops.remove(p.from)
            stops.add(p.to)
            state = State(state.currentFloor, p.direction, p.direction)
          }
          pickUpsHere.foreach(p => pickUps.remove(p))
  }
}