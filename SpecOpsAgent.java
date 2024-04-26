package edu.bu.labs.infexf.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;


import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Stack;


// JAVA PROJECT IMPORTS
import edu.bu.labs.infexf.distance.DistanceMetric;
import edu.bu.labs.infexf.graph.Path;
import edu.bu.labs.infexf.graph.Vertex;


/**
 * The base type for {@link Agent}'s that solve mazes in Sepia. SpecOpsAgent implement a specific state machine that
 * allows them to move from one coordinate on the map to another using a precomputed plan (i.e. a path).
 * 
 * {@link Agent}s in Sepia must override a few abstract methods, most notably <code>initialStep</code>,
 * <code>middleStep</code>, and <code>terminalStep</code>. All of these methods are handled for you, to both control
 * the API (and prevent you from issuing Sepia compound actions) as well as provide an API for the autograders to use.
 *
 * In this assignment, you will need to implement two methods:
 * <code>public Path search(Vertex src, Vertex goal, StateView state)</code>.
 * This method is called by SpecOpsAgent to calculate the path (called a plan) from the source {@link Vertex} to the
 * goal {@link Vertex}. Note that the goal {@link Vertex} is occupied (by the enemy base), and therefore your plan
 * should not ultimately contain this vertex in it.
 *
 * <code> public boolean shouldReplacePlan(StateView state)</code>
 * This method is called by SpecOpsAgent to decide if the current plan is no longer valid (for instance if the current
 * plan is blocked by an enemy unit or something). If this method returns <code>true</code>, then SpecOpsAgent will call
 * the <code>search</code> method again to calculate a new plan, which it will then follow.
 *
 * Here is a description of how these three methods are implemented:
 * <code>initialStep</code>:
 *    This method discovers the unit you control, the enemy base's unit, and all other enemy units present on the map.
 *    Once this is complete, this method calls the <code>search</code> method to find a plan from your unit's
 *    current location to the location of the enemy's base. The path produced by this method is converted into a Stack
 *    of coordinates for internal use.
 *
 * <code>middleStep</code>:
 *    This method first calls the <code>shouldReplacePlan</code> method to determine if the current plan is still valid.
 *    If <code>shouldReplacePlan</code> returns <code>true</code>, then this method will call <code>search</code>,
 *    convert the result to a Stack of coordinates, and overwrite the current plan with this new plan.
 *
 *    This method will then examine the current position of the unit you control. If the current plan is not empty,
 *    this method will try to move the unit to the next position in the current plan, and will fail if the coordinate
 *    to move to is not adjacent to the current position of the unit you control. When the plan is exhausted (i.e. there
 *    are no more coordinates in the Stack), this unit will try to attack the enemy base. If the unit you control is
 *    not adjacent to the enemy base, this method will crash the program.
 *
 * <code>terminalStep</code>:
 *    This method prints out the results of the game. There are four possible outcomes:
 *        1) both your unit and the enemy base are killed. This is a tie.
 *        2) the enemy base is killed and you survive. You win.
 *        3) the enemy base survives and your unit is killed. You lose.
 *        4) both the enemy base as well as you survive. This is a tie.
 *
 * @author          Andrew Wood
 * @see             Vertex
 * @see             Path
 * @see             SpecOpsAgent#search
 * @see             SpecOpsAgent#shouldReplacePlan
 */
public abstract class SpecOpsAgent
    extends Agent
{

    public static enum AgentPhase
    {
        INFILTRATE,
        EXFILTRATE;
    }

    private int             myUnitID;
    private int             enemyTargetUnitID;
    private Set<Integer>    otherEnemyUnitIDs;
    private Stack<Vertex>   currentPlan;
    private Vertex          nextVertexToMoveTo;
    private AgentPhase      agentPhase;
    private Vertex          entryPointVertex;


    public SpecOpsAgent(int playerNum)
    {
        super(playerNum);
        this.myUnitID = -1;                                         // invalid state
        this.enemyTargetUnitID = -1;                                // invalid state
        this.otherEnemyUnitIDs = null;
        this.currentPlan = null;
        this.nextVertexToMoveTo = null;
        this.agentPhase = AgentPhase.INFILTRATE;
        this.entryPointVertex = null;
    }

    /**
     * A getter method to get the unit id of the unit under your control.
     *
     * @return      The id of the unit under your control.
     */
    public int getMyUnitID() { return this.myUnitID; }

    /**
     * A getter method to get the unit id of the enemy base.
     *
     * @return      The id of the enemy base.
     */
    public int getEnemyTargetUnitID() { return this.enemyTargetUnitID; }

    /**
     * A getter method to get the ids of all other enemy units on the map (not including the enemy base).
     *
     * @return      The ids of all other enemy units on the map (not including the enemy base).
     */
    public final Set<Integer> getOtherEnemyUnitIDs() { return this.otherEnemyUnitIDs; }

    /**
     * A getter method to get the current plan to get from your unit's current location to a square
     * adjacent to the enemy base
     *
     * @return      The plan (i.e. sequence of coordinates) to get from your unit's current location to a square
     *              adjacent to the enemy base
     */
    public final Stack<Vertex> getCurrentPlan() { return this.currentPlan; }

    /**
     * A getter method to get the next coordinate to move the unit you control to. This is used internally
     * by <code>middleStep</code> just in case movement takes more than a single turn. This is updated
     * every time the unit arrives at this coordinate and is set to the next coordinate in the plan (or null if
     * the plan is empty)
     *
     * @return      The coordinate to move the unit you control to.
     */
    public final Vertex getNextVertexToMoveTo() { return this.nextVertexToMoveTo; }

    /**
     * A getter method to get the phase that this Agent currently is in. There are two phases:
     * INFILTRATE and EXFILTRATE.
     *
     * @return      The phase this Agent is currently in
     */
    public final AgentPhase getAgentPhase() { return this.agentPhase; }

    /**
     * A getter method to get the Vertex that our unit initially was located on in the map. We will exfiltrate here.
     *
     * @return      The Vertex our unit was initially at in the map.
     */
    public final Vertex getEntryPointVertex() { return this.entryPointVertex; }

    /**
     * A setter method to set the unit id of the unit under your control.
     *
     * @param unitID      The id of the unit under your control.
     */
    private void setMyUnitID(int unitID) { this.myUnitID = unitID; }

    /**
     * A setter method to set the unit id of the enemy base.
     *
     * @param unitID      The id of the enemy base
     */
    private void setEnemyTargetUnitID(int unitID) { this.enemyTargetUnitID = unitID; }

    /**
     * A setter method to set the unit ids of all other enemy units on the map (not including the enemy base)
     *
     * @param unitIDs     The ids of all other enemy units on the map (not including the enemy base)
     */
    private void setOtherEnemyUnitIDs(Set<Integer> unitIDs) { this.otherEnemyUnitIDs = unitIDs; }

    /**
     * A setter method to set the current plan (i.e. Stack of coordinates). <code>middleStep</code> will follow this
     * plan by moving the unit you control to each coordinate in the plan one at a time.
     *
     * @param newPlan      The new plan to follow in <code>middleStep</code>
     */
    protected void setCurrentPlan(Stack<Vertex> newPlan) { this.currentPlan = newPlan; }

    /**
     * A setter method to set the coordinate to move the unit you control to. This is called by <code>middleStep</code>
     * whenever the unit you control arrives at the coordinate contained in the field this method sets. Once that
     * happens, <code>middleStep</code> pops the next coordinate from the plan and uses this method to remember that
     * coordinate in case movement takes more than one turn.
     *
     * @param v      The coordinate to move the unit you control to in <code>middleStep</code>
     */
    private void setNextVertexToMoveTo(Vertex v) { this.nextVertexToMoveTo = v; }

    /**
     * A setter method to set the phase that this Agent is currently in.
     *
     * @param p      The phase that this agent should now be in.
     */
    private void setAgentPhase(AgentPhase p) { this.agentPhase = p; }

    /**
     * A setter method to set the Vertex that our unit was initially at on the map.
     *
     * @param v      The Vertex that our unit was initially at on the map
     */
    private void setEntryPointVertex(Vertex v) { this.entryPointVertex = v; }

    /**
     * A method to discover all units on the map. There should be only one unit you control, one enemy base, and an
     * arbitrary number of additional units the enemy controls. This method, after discovering the ids of these
     * units and setting fields, will call <code>search</code> to find the plan from your unit's current location
     * to a coordinate adjacent to the enemy base.
     *
     * @param state     The initial state of the game.
     * @param history   The initial history of the game.
     * @return          This method returns <code>null</code> and relies on <code>middlestep</code> to do any movement.
     */
    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        // first find out which units are mine and which units aren't
        Set<Integer> myUnitIDs = new HashSet<Integer>();
        for(Integer unitID : state.getUnitIds(this.getPlayerNumber()))
        {
            myUnitIDs.add(unitID);
        }

        // should only be one unit controlled by me
        if(myUnitIDs.size() != 1)
        {
            System.err.println("ERROR: should only be 1 unit controlled by player=" +
                this.getPlayerNumber() + " but found " + myUnitIDs.size() + " units");
            System.exit(-1);
        } else
        {
            this.setMyUnitID(myUnitIDs.iterator().next()); // get the one unit id
        }


        // there can be as many other players as we want, and they can controll as many units as they want,
        // but there should be only ONE enemy townhall unit
        Set<Integer> enemyTownhallUnitIDs = new HashSet<Integer>();
        Set<Integer> otherEnemyUnitIDs = new HashSet<Integer>();
        for(Integer playerNum : state.getPlayerNumbers())
        {
            if(playerNum != this.getPlayerNumber())
            {
                for(Integer unitID : state.getUnitIds(playerNum))
                {
                    if(state.getUnit(unitID).getTemplateView().getName().toLowerCase().equals("townhall"))
                    {
                        enemyTownhallUnitIDs.add(unitID);
                    } else
                    {
                        otherEnemyUnitIDs.add(unitID);
                    }
                }
            }
        }

        // should only be one unit controlled by me
        if(enemyTownhallUnitIDs.size() != 1)
        {
            System.err.println("ERROR: should only be 1 enemy townhall unit present on the map but found "
                + enemyTownhallUnitIDs.size() + " such units");
            System.exit(-1);
        } else
        {
            this.setEnemyTargetUnitID(enemyTownhallUnitIDs.iterator().next()); // get the one unit id
            this.setOtherEnemyUnitIDs(otherEnemyUnitIDs);
        }
        this.setEntryPointVertex(new Vertex(state.getUnit(this.getMyUnitID()).getXPosition(),
            state.getUnit(this.getMyUnitID()).getYPosition()));

        this.setCurrentPlan(this.makePlan(state));

        return null;
    }

    /**
     * A method to decide what action the unit you control should do every turn. This method decides what to do using
     * the following algorithm:
     *    1) if the current plan is invalid (i.e. <code>this.shouldReplacePlan(state)</code> returns <code>true</code>), then
     *       the current plan is replaced by a new plan calculated by <code>search</code> (technically it goes through a
     *       separate method called <code>makePlan</code>. This additional method calls <code>search</code> and then
     *       converts the {@link Path} object to a {@link Stack} of coordinates.
     *
     *    2) If the plan is not empty and the unit you control is not currently at the {@link Vertex} to move to,
     *       then move to that coordinate. This can fail if the two coordinates are not adjacent.
     *
     *    3) If the plan is empty, attack the enemy base. If the unit you control is not adjacent to the enemy base,
     *       this method will print an error and crash the program.
     *
     * @param state         The current state of the game
     * @param history       The history of the game up to this turn
     * @return              A mapping from every unit id under our control to an {@link Action} that we want that unit
     *                      to do.
     */
    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        // state machine: detect phase change and upon the phase change replan
        if(state.getUnit(this.getEnemyTargetUnitID()) == null && this.getAgentPhase().equals(AgentPhase.INFILTRATE))
        {
            this.setAgentPhase(AgentPhase.EXFILTRATE);
            this.setCurrentPlan(this.makePlan(state));
        } else if(this.shouldReplacePlan(state)) // state machine: if we should replace the current plan, do so
        {
            this.setCurrentPlan(this.makePlan(state));
        }

        // get my position
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        Vertex myUnitVertex = new Vertex(myUnitView.getXPosition(), myUnitView.getYPosition());
        UnitView enemyTargetUnitView = state.getUnit(this.getEnemyTargetUnitID());

        if(!this.getCurrentPlan().isEmpty() && (this.hasArrivedAtNextVertex(state)))
        {
            // pop off the next element of the plan
            this.setNextVertexToMoveTo(this.getCurrentPlan().pop());
            System.out.println("Moving to " + this.getNextVertexToMoveTo());
        }

        
        if(this.getNextVertexToMoveTo() != null && !this.getNextVertexToMoveTo().equals(myUnitVertex))
        {
            // try to go there
            actions.put(this.getMyUnitID(),
                        Action.createPrimitiveMove(this.getMyUnitID(),
                                                   this.getDirectionToMoveTo(myUnitVertex,
                                                                             this.getNextVertexToMoveTo())));
        } else
        {
            // enemy target is still alive
            if(enemyTargetUnitView != null)
            {
                if(Math.abs(myUnitView.getXPosition() - enemyTargetUnitView.getXPosition()) > 1 ||
                   Math.abs(myUnitView.getYPosition() - enemyTargetUnitView.getYPosition()) > 1)
                {
                    System.err.println("ERROR: plan is empty, so we should be next to the enemy target"
                        + " it seems we're not. Cannot attack enemy.");
                } else
                {
                    System.out.println("attacking enemy");
                    actions.put(this.getMyUnitID(),
                        Action.createPrimitiveAttack(this.getMyUnitID(),
                                                     this.getEnemyTargetUnitID()));
                }
            }
        }

        return actions;
    }

    /**
     * A method to print out the outcome of the game. There are four possible outcomes:
     *      1) both your unit and the enemy base are killed. This is a tie.
     *      2) the enemy base is killed and you survive. You win.
     *      3) the enemy base survives and your unit is killed. You lose.
     *      4) both the enemy base as well as you survive. This is a tie.
     *
     * @param state         The last state of the game.
     * @param history       The entire history of the game.
     */
    @Override
    public void terminalStep(StateView state,
                             HistoryView history)
    {
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        UnitView enemyTargetUnitView = state.getUnit(this.getEnemyTargetUnitID());

        if(myUnitView == null && enemyTargetUnitView == null)
        {
            System.err.println("The enemy was destroyed, but so were you!");
        } else if(myUnitView != null && enemyTargetUnitView == null)
        {
            System.out.println("The enemy was destroyed, you win!");
        } else if(myUnitView == null && enemyTargetUnitView != null)
        {
            System.err.println("You were destroyed, you lose!");
        } else
        {
            System.out.println("Both you and the enemy lived another day");
        }
    }

    /**
     * A method to save this MazeAgent to a file on disk. This method does nothing.
     *
     * @param os        The {@link OutputStream} to write to
     */
    @Override
    public void savePlayerData(OutputStream os) {}

    /**
     * A method to load this MazeAgent from disk. This method does nothing.
     *
     * @param is        The {@link InputStream} to write to
     */
    @Override
    public void loadPlayerData(InputStream is) {}

    /**
     * A method to make a new plan from the current state of the game. This method, when infiltrating, will call the
     * <code>search</code> method to calculate a {@link Path} from the current location of your unit to the enemy base,
     * and when exfiltrating, will call <code>search</code> to calculate a {@link Path} from the current location of
     * your unit to the square your unit initially occupied at the beginning of the game.
     *
     * This method then creates a {@link Stack} of coordinates from this {@link Path}, which is used internally by
     * <code>middleStep</code> to move the unit you control.
     *
     * @param state     The current state of the game.
     * @return          The {@link Stack} of coordinates to follow, called a plan.
     */
    protected Stack<Vertex> makePlan(StateView state)
    {
        Vertex srcVertex = new Vertex(state.getUnit(this.getMyUnitID()).getXPosition(),
                                      state.getUnit(this.getMyUnitID()).getYPosition());
        Vertex dstVertex = null;
        switch(this.getAgentPhase())
        {
            case INFILTRATE:
                dstVertex = new Vertex(state.getUnit(this.getEnemyTargetUnitID()).getXPosition(),
                                       state.getUnit(this.getEnemyTargetUnitID()).getYPosition());
                break;
            case EXFILTRATE:
                dstVertex = this.getEntryPointVertex();
                break;
            default:
                System.err.println("[ERROR] SpecOpsAgent.makePlan: unknown agent phase=" + this.getAgentPhase());
                System.exit(-1);
                break;
        }

        Path path = this.search(srcVertex, dstVertex, state);

        // convert a Path into a Stack<Vertex>
        Stack<Vertex> plan = new Stack<Vertex>();

        // remember that search cuts off the last vertex, so we need to add back the dst if we're exfiltrating
        if(this.getAgentPhase().equals(AgentPhase.EXFILTRATE))
        {
            plan.push(this.getEntryPointVertex());
        }

        while(path.getParentPath() != null)
        {
            plan.push(path.getDestination());
            path = path.getParentPath();
        }

        return plan;
    }

    /**
     * A helper method to calculate the sepia {@link Direction} needed to go from the source coordinate to the
     * destination coordiante. This method will return <code>null</code> (and print out an error) if the two
     * coordinates are not adjacent.
     *
     * @param src   The source coordinate
     * @param dst   The destination coordinate
     * @return      The sepia {@link Direction} that will move a unit located at the source coordinate to the
     *              destination coordinate. Will be <code>null</code> if the two coordinates are not adjacent.
     */
    protected Direction getDirectionToMoveTo(Vertex src, Vertex dst)
    {
        int xDiff = dst.getXCoordinate() - src.getXCoordinate();
        int yDiff = dst.getYCoordinate() - src.getYCoordinate();

        Direction dirToGo = null;

        if(xDiff == 1 && yDiff == 1)
        {
            dirToGo = Direction.SOUTHEAST;
        }
        else if(xDiff == 1 && yDiff == 0)
        {
            dirToGo = Direction.EAST;
        }
        else if(xDiff == 1 && yDiff == -1)
        {
            dirToGo = Direction.NORTHEAST;
        }
        else if(xDiff == 0 && yDiff == 1)
        {
            dirToGo = Direction.SOUTH;
        }
        else if(xDiff == 0 && yDiff == -1)
        {
            dirToGo = Direction.NORTH;
        }
        else if(xDiff == -1 && yDiff == 1)
        {
            dirToGo = Direction.SOUTHWEST;
        }
        else if(xDiff == -1 && yDiff == 0)
        {
            dirToGo = Direction.WEST;
        }
        else if(xDiff == -1 && yDiff == -1)
        {
            dirToGo = Direction.NORTHWEST;
        } else
        {
            System.err.println("ERROR: cannot go from src=" + src + " to dst=" + dst + " in one move.");
        }

        return dirToGo;
    }

    /**
     * A helper method to determine if the unit you control has arrived at the coordinate returned by
     * <code>getNextVertexToMoveTo</code>. This method will return <code>true</code> if
     * <code>getNextVertexToMoveTo</code> returns <code>null</code>.
     *
     * @param state     The current state of the game
     * @return          <code>true</code> if the coordinate of the unit you control equals the coordinate produced by
     *                  <code>getNextVertexToMoveTo</code> or if <code>getNextVertexToMoveTo</code> returns
     *                  <code>null</code>. <code>false</code> otherwise.
     */
    protected boolean hasArrivedAtNextVertex(StateView state)
    {
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        return this.getNextVertexToMoveTo() == null ||
            this.getNextVertexToMoveTo().equals(new Vertex(myUnitView.getXPosition(), myUnitView.getYPosition()));
    }


    /**
     * A helper method to determine to get the collection of {@link Vertex} objects that are neighbors of the
     * input {@link Vertex}. Neighbor vertices must have coordinates that are in bounds of the map,
     * cannot be occupied by a resource (i.e. a Tree or Gold), and also cannot be occupied by a unit. An exception
     * is made for the enemy base's unit, we allow a {@link Vertex} that is occupied by the enemy base to appear
     * in a neighborhood. This is so that the <code>search</code> method will find the enemy base's location
     * (since it is used as the goal {@link Vertex} when infiltrating). The enemy base will be dead (i.e. not present
     * on the map) when exfiltrating, so we do not have to worry about trying to move into an occupied square when
     * exfiltrating.
     *
     * @param v         The {@link Vertex} to find the neighbors of
     * @param state     The current sepia state of the game.
     * @return          A collection of {@link Vertex} objects that are considered adjacent to the input {@link Vertex}.
     */
    public Collection<Vertex> getNeighbors(Vertex v,
                                           StateView state)
    {
        Collection<Vertex> neighbors = new HashSet<Vertex>();

        // find all coordinates of neighbors
        for(int xOffset : new int[]{-1, 0, +1})
        {
            for(int yOffset : new int[]{-1, 0, +1})
            {
                if(!(xOffset == 0 && yOffset == 0))
                {
                    Vertex potentialNeighbor = new Vertex(
                        v.getXCoordinate() + xOffset,
                        v.getYCoordinate() + yOffset
                    );

                    // is this a valid neighbor?
                    if(state.inBounds(potentialNeighbor.getXCoordinate(),
                                      potentialNeighbor.getYCoordinate())
                       && !state.isResourceAt(potentialNeighbor.getXCoordinate(),
                                              potentialNeighbor.getYCoordinate())
                       && (!state.isUnitAt(potentialNeighbor.getXCoordinate(),
                                           potentialNeighbor.getYCoordinate())
                           || state.unitAt(potentialNeighbor.getXCoordinate(),
                                           potentialNeighbor.getYCoordinate()) ==
                                this.getEnemyTargetUnitID()))
                    {
                        neighbors.add(potentialNeighbor);
                    }
                }
            }
        }

        return neighbors;
    }

    /**
     * A helper method to calculate the heuristic value of a vertex (i.e. an estimate of the remaining path cost)
     * to the goal state. This is calculated as the euclidean distance (i.e. straight line distance) between the
     * coordinates of the squares on the map.
     *
     * @param v         The {@link Vertex} to find the heuristic value of.
     * @param goal      The goal {@link Vertex}
     * @param state     The current sepia state of the game.
     * @return          The euclidean distance between the two vertices.
     */
    public float getHeuristicValue(Vertex v,
                                   Vertex goal,
                                   StateView state)
    {
        return DistanceMetric.euclideanDistance(v, goal);
    }

    /**
     * A method for a graph traversal algorithm that computes the path from the source coordinate to a
     * coordinate adjacent to the goal coordinate using the {@link Path} datatype. This uses the A* algorithm.
     *
     * @param src       The source coordinate
     * @param goal      The goal coordinate
     * @return          A {@link Path} object that implements a path from the source coordinate to a coordinate
     *                  that is adjacent to the goal coordinate. Do not include the goal coordinate in this path.
     */
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {
        PriorityQueue<Path> heap = new PriorityQueue<Path>(
            new Comparator<Path>()
            {
                @Override
                public int compare(Path a,
                                   Path b)
                {
                    return Float.compare(a.getEstimatedPathCostToGoal() + a.getTrueCost(),
                                         b.getEstimatedPathCostToGoal() + b.getTrueCost());
                }
            }
        );
        Set<Vertex> finalizedVertices = new HashSet<Vertex>();
        Map<Vertex, Float> dstVertex2SmallesTotalEstimatedPathCostSoFar = new HashMap<Vertex, Float>();

        heap.add(new Path(src));
        dstVertex2SmallesTotalEstimatedPathCostSoFar.put(src, 0f);

        while(!heap.isEmpty())
        {
            Path shortestPath = heap.poll();
            finalizedVertices.add(shortestPath.getDestination());

            if(shortestPath.getDestination().equals(goal))
            {
                return shortestPath.getParentPath();
            }

            for(Vertex neighbor : this.getNeighbors(shortestPath.getDestination(), state))
            {
                if(!finalizedVertices.contains(neighbor))
                {
                    float edgeWeight = this.getEdgeWeight(shortestPath.getDestination(), neighbor, state);
                    float heuristicValue = this.getHeuristicValue(neighbor, goal, state);
                    float totalEstimatedPathCost = shortestPath.getTrueCost() + edgeWeight + heuristicValue;

                    if(dstVertex2SmallesTotalEstimatedPathCostSoFar.containsKey(neighbor))
                    {
                        if(dstVertex2SmallesTotalEstimatedPathCostSoFar.get(neighbor) > totalEstimatedPathCost)
                        {
                            Path newPath = new Path(neighbor, edgeWeight, heuristicValue, shortestPath);
                            heap.remove(newPath);
                            heap.add(newPath);
                            dstVertex2SmallesTotalEstimatedPathCostSoFar.put(neighbor, totalEstimatedPathCost);
                        }
                    } else
                    {
                        heap.add(new Path(neighbor, edgeWeight, heuristicValue, shortestPath));
                        dstVertex2SmallesTotalEstimatedPathCostSoFar.put(neighbor, totalEstimatedPathCost);
                    }
                }
            }
        }

        return null;
    }

    /**
     * An abstract method to calculate the edge weight between a source {@link Vertex} to a destination {@link Vertex}.
     * This edge weight can be a function of what is happening in the game currently, and is a nice way to encode
     * "risk". The can incentivize A* to use this edge in a path by making the edge weight small, and we can discourage
     * A* from using this edge in a path by making the edge weight large. So, we can assign large edge weights when
     * taking that edge leads to bad scenarios (like going to a place thats close to the enemy units), etc. and we can
     * assign smaller weights when taking that edge leads to good scenarios.
     * <p>
     * Just remember that we are using euclidean distance, which must be admissible and consistent in this world.
     * Typically I ask you to design a heuristic that is admissible and consistent where we already know the edge
     * weights. Here we must work backwards. In order for euclidean distance to be admissible, an edge weight
     * cannot be &lt; 1, so make sure your edge weights are ^gt;= 1! I'll leave it up to you to decide whether your
     * edge weights allow euclidean distance to be consistent.
     */
    public abstract float getEdgeWeight(Vertex src,
                                        Vertex dst,
                                        StateView state);

    /**
     * An abstract method to determine if the current plan (i.e. a {@link Stack} of coordinates) is still valid.
     * Plans can become invalid for a variety of reasons: maybe something has moved in the world and is now blocking
     * one of the coordinates you intend to traverse, or maybe the plan takes your unit too close to an enemy that
     * will kill it, etc.
     *
     * @param state     The current state of the game
     * @return          <code>true</code> if the current plan should be recalculated, <code>false</code> otherwise.
     */
    public abstract boolean shouldReplacePlan(StateView state);

    // personally added 
    public String reportNextMoveAndWeight(StateView state) {
        // Check if there is a current plan and it's not empty.
        if (this.getCurrentPlan() == null || this.getCurrentPlan().isEmpty()) {
            return "No current plan or the plan is empty.";
        }
    
        // Assuming the current position is where the unit is right now.
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        Vertex currentPosition = new Vertex(myUnitView.getXPosition(), myUnitView.getYPosition());
    
        // The next vertex to move to, already stored in your agent.
        Vertex nextVertex = this.getNextVertexToMoveTo();
    
        // Calculate the edge weight between the current position and the next vertex.
        float edgeWeight = this.getEdgeWeight(currentPosition, nextVertex, state);
    
        // Compile the information into a string for output or return.
        String report = "Current Position: " + currentPosition +
                        ", Next Vertex: " + nextVertex +
                        ", Edge Weight to Next Vertex: " + edgeWeight;
    
        // For demonstration, print the report. Alternatively, you can return the string.
        System.out.println(report);
        return report;
    }

}

