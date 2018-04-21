package com.lightbend.akka.sample

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }

object ElevatorSim extends App {
  import ElevatorActor._
  import Controller._
  import IOActor._

  val system: ActorSystem = ActorSystem("elevatorSim")

  val ioActor: ActorRef = system.actorOf(IOActor.props, "IOActor")
  
  val simController: ActorRef = 
    system.actorOf(Controller.props("Controller", ioActor), "controller")
  
  import scala.io.StdIn  
  val PickupRegEx = "\\s*>*\\(([0-9]+),\\s*([0-9]+)\\)\\s*".r
  val AddElevatorRegEx = "\\s*>*AddElevator\\((.+),\\s*\\(([0-9]+),\\s*([0-9]+)\\)\\)\\s*".r
  val ServiceElevatorRegEx = "\\s*>*ServiceElevator\\((.+)\\)\\s*".r
  val ManualOperationRegEx = "\\s*@manual\\s*".r
  val NextRegEx ="\\s*n\\s*".r
  val DisableManualOperationRegEx = "\\s*-manual\\s*".r
  val BulkPickupRegEx = "\\s*bulk\\s*".r
  val HelpRegEx = "\\s*help\\s*".r
  val ExitRegEx = "\\s*exit\\s*".r
  val BasicInit = "\\s*init\\s*([0-9]+)\\s*".r
  val Dashboard = "\\s*dashboard\\s*".r
  
  
  
  var runForever = true
  while(runForever) {
    val request = StdIn.readLine() match {
      case PickupRegEx(from, to) => {
          val source: Int = from.toInt
          val dest: Int = to.toInt
          if(source==dest) InvalidRequest(s"Going nowhere?!")
          else RequestPickup(PickUp(source, dest))
        }
      case BulkPickupRegEx() => {
        ioActor ! Message("Enter one pickup in each line. \"done\" to schedule pickups.")
        var req = StdIn.readLine()
        var bulkReq: List[RequestPickup] = List()
        while(req != "done"){ req match {
          case PickupRegEx(from, to) => {
            val source: Int = from.toInt
            val dest: Int = to.toInt
            if(source==dest) InvalidRequest(s"Going nowhere?!")
            else bulkReq :+ RequestPickup(PickUp(source, dest))
            }
          }
        req = StdIn.readLine()
        }
        BulkRequest(bulkReq)
      }
      case AddElevatorRegEx(name, from, to) => AddElevator(name, (from.toInt, to.toInt))
      case ServiceElevatorRegEx(name) => ServiceElevator(name)
      case ManualOperationRegEx() => StartManualMode
      case NextRegEx() => NextStep
      case DisableManualOperationRegEx() => DisableManualMode
      case HelpRegEx() => DisplayHelp
      case BasicInit(n) => InitializeWithDefaults(n.toInt)
      case Dashboard() => DisplayDash
      case ExitRegEx() => 
        {
          system.terminate()
          runForever = false
        }
      case req => InvalidRequest(s"$req\nPlease try again or take the stairway to heaven!")
    }
    simController ! request
  }
}
