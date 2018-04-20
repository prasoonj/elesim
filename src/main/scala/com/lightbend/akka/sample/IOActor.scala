package com.lightbend.akka.sample

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.io.StdIn
import akka.actor.PoisonPill

object IOActor {
  def props: Props = Props[IOActor]
  final case class Message(msg: String)
  final case class MessageFrom(msg: String)
  final case object Help
  final case object StartIOLoop
  
  trait RequestResponseCode
  
  final case class RequestUserInput(code: RequestResponseCode, msg: String)
  final case class UserResponse(code: RequestResponseCode, response: String)
  
  val helpMessage = """
    __                                        __                                   
/    /                /                   /    /           /      /             
(___ (  ___       ___ (___  ___  ___      (___    _ _      (  ___ (___  ___  ___ 
|    | |___) \  )|   )|    |   )|   )         )| | | )|   )| |   )|    |   )|   )
|__  | |__    \/ |__/||__  |__/ |          __/ | |  / |__/ | |__/||__  |__/ |    


Use the following commands to interact with the simulator:
AddElevator(3, (0,13)) #Elevator number, operation range
ServiceElevator(3)     #Takes it off the grid!
(4, 8)                 #Pickup request: (from, to)
bulk                   #Provide inputs for bulk pickup
[(1,10),(4,1),(1,8)]   #Bulk pickup requests
@manual                #Manual operation (disables the ticker)
-manual                #Disable manual operation
init 16                #Initialize with 16 elevators
dashboard              #Quick look at all elevators
help                   #Bring up this menu
exit                   #To exit the simulation
>>"""
}

class IOActor extends Actor with ActorLogging {
  import IOActor._
  
  override def preStart() = {
    context.self ! Message(helpMessage)
  }
  
  def receive = {
    case Message(message) =>
      println(message)
    case MessageFrom(msg) =>
      println(s"${context.sender().path} says: $msg")
    case Help => println(helpMessage)
  }
}