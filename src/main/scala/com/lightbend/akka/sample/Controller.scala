package com.lightbend.akka.sample

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.collection.mutable.HashSet
import akka.actor.Timers

object Controller {
  def props(message: String, ioActor: ActorRef): Props = Props(new Controller(message, ioActor))
  
  case object Greet
  case object Start
  
  sealed trait Direction
  case object Limbo extends Direction
  case object Down extends Direction
  case object Up extends Direction
  
  
  final case class InitializeWithDefaults(n: Int)
  final case class PickUp(from: Int, to: Int) {
    val direction: Direction = 
    if(from < to) Up
    else Down
  }
  final case class RequestPickup(pickUp: PickUp)
  final case class CanPickup(pickUp: PickUp)
  final case class ExistingStop(pickUp: PickUp)
  final case class AddPickup(pickUp: PickUp)
  final case class BulkRequest(rs: List[RequestPickup])
  final case class RequestStop(from: Int, to: Int, direction: String)
  final case class InvalidRequest(message: String)
  
  final case class AddElevator(name: String, range: (Int, Int))
  final case class ServiceElevator(name: String)
  
  final case object StartManualMode
  final case object DisableManualMode
  final case object NextStep
  final case object DisplayHelp
  final case object DisplayDash
}

class Controller(message: String, ioActor: ActorRef) extends Actor {
  import IOActor._
  import Controller._
  import ElevatorActor._
  
  var elevatorsInService: scala.collection.mutable.Set[String] = HashSet.empty
  var stopRequests: scala.collection.mutable.Set[PickUp] = HashSet.empty

  def receive = {
    case Start => ioActor ! StartIOLoop
    case InitializeWithDefaults(ns) =>
      for(n <- (1 to ns)) {
        self ! AddElevator(n.toString, (0, 16))
      }
    case AddElevator(name, range) =>
      val elevatorRef: ActorRef = context.actorOf(ElevatorActor.props(name, range, ioActor), name)
      elevatorsInService.add(name)
      ioActor ! Message(s"Added elevator $name. Operates between $range.")
    case ServiceElevator(name) =>
      elevatorsInService.remove(name)
      ioActor ! Message(s"Elevator $name is under maintanance")
    case RequestPickup(pickUp) =>
      stopRequests.add(pickUp)
      ioActor ! Message(s"You are on ${pickUp.from} and you want to go ${pickUp.direction}")
      context.children.foreach( child => child ! RequestPickup(pickUp)) 
    case BulkRequest(rs) => rs.foreach { r => self ! r }
    case ExistingStop(pickUp) =>
      stopRequests.remove(pickUp)
    case CanPickup(pickUp) => 
      if(stopRequests.contains(pickUp)) {
        context.sender() ! AddPickup(pickUp)
        ioActor ! MessageFrom(s"Asking ${context.sender()} to stop at ${pickUp.from}")
        stopRequests.remove(pickUp)
      }
    case StartManualMode => {
      ioActor ! Message("Press 'n' to run through the simulation")
      context.children.map( e => e ! StartManualMode )
    }
    case DisableManualMode => {
      ioActor ! Message("Manual mode disabled")
      context.children.map( e => e ! DisableManualMode )
    }
    case NextStep => context.children.map( e => e ! NextStep )
    case DisplayHelp => ioActor ! Help
    case DisplayDash => 
      ioActor ! Message(s"Pending pickups: $stopRequests")
      context.children.map( e => e ! ReportStatus)
    case InvalidRequest(message) =>
      ioActor ! Message(s"$message")
    case x => ioActor ! MessageFrom(s"Something went wrong with this message: $x")
  }
}


