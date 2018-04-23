# Elevator Simulator

## Running the simulation
This project is built on `sbt` so please ensure you have `sbt` installed ( `$ brew install sbt` on Mac ) 

Navigate to the root of this project and:

``` sh
$ sbt
> run
# This should bring up the menu for the simulator as shown below:

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
>>

# Begin by initializing a few elevators:
>>
init 6
Added elevator 1. Operates between (0,16).
Added elevator 2. Operates between (0,16).
Added elevator 3. Operates between (0,16).
Added elevator 4. Operates between (0,16).
Added elevator 5. Operates between (0,16).
Added elevator 6. Operates between (0,16).
```

## Solution outline and motivation
The problem statement demands a solution that requires a concurrent operation of elevators, with a necessity to exchange messages
with each other to communicate their states. A good way to model this is to use CQRS/Event Sourcing to ensure that internal states
are not corrupted and, effective communication happens between the elevators.
This simulation leverages `akka actors`. 
The elevator `Controller` is modeled as the top level actor (child actor of the `ActorSystem`) 
while the individual elevators are child actors of the `Controller`. All IO operations are carried out by an `IOActor` (in a real-world
system, this would be the display panel, etc.)
The advantages of this approach are:
 1. Elevators can be added or taken off the grid for servicing at any point in time without affecting the operation of others.
 2. It becomes easy to reason about `Pickup requests` (they are dispatched by the `Controller` and handled by `ElevatorActor`s
 3. Scheduling (as we'll see below) can be done with better efficiency.
 
## Scheduling Pickup Requests
We can look at each new `PickUpRequest` as a vector (with a given direction( `Up` or `Down`) , a starting point and an ending point). 
This vector can be included in a currently running elevator iff it aligns with the elevator, which has a direction and a range of
operation of its own!

```
(Floors)             1  2  3  4  5  6  7  8  9  10  11  12  13  14 ...            
(New Pickup 3-5)           ------> 
           
(Elevator 1)         --->  ------>  --->   -------->
(Elevator 2)         --->----->
(Elevator 3)                      <-------          <-------------
```

In the case above, The new pickup "aligns" with Elevator 1 and, should be included in its pickup stops.

One might want to optimize further by including a `PickupRequest(3,5)` in an elevator in state 
`State(currentFloor=1, currentDirection=UP, pickupDirection=DOWN)` if the pickup is beyond the 5th floor. However, this requires 
that the elevator system knows the destination of a pickup before boarding, which might not be the case for most elevator systems. 

Here's the relevant piece of code that captures the scheduling strategy:
```
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
```
To maximize the usage of elevators that are already in operation, an elevator that is in `Limbo` state (stalled), delays sending
its status to the `Controller`. The `Controller` assigns a pickup to the first elevator that responds with a `CanPickup` message.

## Note on data structures used
  1. All messages are `case class` or `case object` which gives them a convenient immutability to work with.
  2. Strings are avoided in favor of `types` wherever possible to help with pattern matching and type safety.
  3. The `var`s are encapsulated inside the actor classes which are guaranteed to be thread-safe.
  
Below are the data-structures that capture the mutable state of the `Controller` and the `ElevatorActor`s respectively:

```
  //Mutable state of the Controller
  var elevatorsInService: scala.collection.mutable.Set[String] = HashSet.empty
  var stopRequests: scala.collection.mutable.Set[PickUp] = HashSet.empty
  
  //Mutable state of ElevatorActor
  var state = State(0, Limbo, Limbo) 
  var stops: scala.collection.mutable.Set[Int] = HashSet.empty
  var pickUps: scala.collection.mutable.Set[PickUp] = HashSet.empty
  
```
All communication between different instances of actors happens using strict, immutable messages that are part of the `object`.

## Areas of Improvements
  1. Pickup scheduling efficiency could possibly be improved by using `heartbeat` to exchange state between `ElevatorActor` siblings.
  2. `IOActor` could be designed better (possibly remote actors for Input and Output?)
  3. Currently, the first elevator to respond gets to service the `PickupRequest`, this could be improved. The elevator nearest to 
  the pickup point should be given preference. But, this would delay response time to a request since some caching of messages would
  be needed at the `Controller`.
  4. Include robust test cases.
  5. Improve the console output. It seems very verbose and littered with debug messages.
  6. Bulk pickUps needs work.