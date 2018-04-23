//#full-example
package com.snortingcode

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import ElevatorActor._
import IOActor._

import Controller._

//#test-classes
class AkkaQuickstartSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  //#test-classes

  def this() = this(ActorSystem("ElevatorSim"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  //#first-test
  //#specification-example
  "A Greeter Actor" should "pass on a greeting message when instructed to" in {
    //#specification-example
    val testProbe = TestProbe()
    val name = "hello"
    val elevatorActor = system.actorOf(ElevatorActor.props(name, (0,16), testProbe.ref))
    val greetPerson = "Akka"
    elevatorActor ! Message(greetPerson)
    testProbe.expectMsg(500 millis, Message(s"$name, $greetPerson"))
  }
  //#first-test
  
  "The Controller" should "be able to add elevators" in {
    val testProbe = TestProbe()
    val ioActor = system.actorOf(IOActor.props, "IOActor")
    val controller = system.actorOf(Controller.props("Controller", ioActor), "Controller")
    controller ! AddElevator("1", (1,12))
    
    
    
  }
}
//#full-example
