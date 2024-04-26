package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.Set;           // will need for bfs
import java.util.Stack;


// JAVA PROJECT IMPORTS
/* 
Contains(), .equals(Other Object) checks for shallow copies of Object, unitAt, ResourceAt

Creates a new path, extending off of an oldPath, lets us use shallow copies of shared paths
    new Path(newDstVertex, edgeWeightFromOldDstToNewDst, oldPath)
*/
public class BFSMazeAgent
    extends MazeAgent
{

    public BFSMazeAgent(int playerNum)
    {
        super(playerNum);

    }

    // Method to get adjacent vertices of a vertex
    private Set<Vertex> getAdjacentVertices(Vertex vertex, StateView state) {
        Set<Vertex> adjacentVertices = new HashSet<>();
        // Logic to find the adjacent vertices into the HashSet
        int x = vertex.getXCoordinate();
        int y = vertex.getYCoordinate();
        
        // in valid game area
        if (isValid(x, y - 1, state)) adjacentVertices.add(new Vertex(x, y - 1));     // South
        if (isValid(x + 1, y - 1, state)) adjacentVertices.add(new Vertex(x + 1, y - 1)); // Southeast
        if (isValid(x + 1, y, state)) adjacentVertices.add(new Vertex(x + 1, y));     // East
        if (isValid(x + 1, y + 1, state)) adjacentVertices.add(new Vertex(x + 1, y + 1)); // Northeast
        if (isValid(x, y + 1, state)) adjacentVertices.add(new Vertex(x, y + 1));     // North
        if (isValid(x - 1, y + 1, state)) adjacentVertices.add(new Vertex(x - 1, y + 1)); // Northwest
        if (isValid(x - 1, y, state)) adjacentVertices.add(new Vertex(x - 1, y));     // West
        if (isValid(x - 1, y - 1, state)) adjacentVertices.add(new Vertex(x - 1, y - 1)); // Southwest

        return adjacentVertices;
    }
    private boolean isValid(int x, int y, StateView state){
        return (x >= 0 && x < state.getXExtent() && y >= 0 && y < state.getYExtent()) && !state.isResourceAt(x,y);
    }

    private boolean isAdjacent(Vertex v1, Vertex v2) {
        int deltaX = Math.abs(v1.getXCoordinate() - v2.getXCoordinate());
        int deltaY = Math.abs(v1.getYCoordinate() - v2.getYCoordinate());

        // Chebyshev distance is the maximum of deltaX and deltaY
        int chebyshevDistance = Math.max(deltaX, deltaY);

        // Two vertices are adjacent if their Chebyshev distance is 1
        return chebyshevDistance == 1;
    }
    // returns path variable 
    // edge cost 1 

    @Override
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {
        if (src.equals(goal)) {
            return new Path(src); // Path with only the source vertex if source is the goal
        }
        Queue<Path> queue = new LinkedList<>();
        Set<Vertex> visited = new HashSet<>();
        System.out.println("src" + src);
        System.out.println("goal" + goal);
        visited.add(src);
        queue.add(new Path(src)); 

        while(!queue.isEmpty()){
            Path currentPath = queue.poll(); //returns head of queue or null if empty
            Vertex currentVertex = currentPath.getDestination();
            //System.out.println("Current Vertex:" + currentVertex);
            //System.out.println("Current Path:" + currentPath);
            //check for all adjacent vertices 
            for (Vertex adjacent : getAdjacentVertices(currentVertex, state)) {
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Path newPath = new Path(adjacent, 1.0f, currentPath);

                    if (isAdjacent(adjacent, goal)) {
                        return newPath; // Found the goal, return the path
                    }   

                    queue.add(newPath);
                }
            }
        }
        //starts with source vertex
        return null;
    }

    private boolean atTownHall(StateView state, Vertex vertex){
        UnitView unit = state.getUnit(state.unitAt(vertex.getXCoordinate(), vertex.getYCoordinate()));
        String unitName = unit.getTemplateView().getName();
        if (unitName.equals("TownHall")){
            return true;
        } else {
            return false;
        }
    }
    
    //runs search function that checks if state can move onto the next step, essentially the middlestep
    // if the path is clear or not, access path variable using MazeAgent
    @Override
    public boolean shouldReplacePlan(StateView state)
    {   
        Stack<Vertex> curPlan = this.getCurrentPlan();
        // copy currentPlan
        Stack<Vertex> tmp = new Stack<>();
        tmp.addAll(curPlan); //all ellements have stack over

        while(!tmp.isEmpty()){
            Vertex vertex = tmp.pop(); // pops vertex from stack
            int verX = vertex.getXCoordinate();
            int verY = vertex.getYCoordinate();

            if (state.isUnitAt(verX, verY) && !atTownHall(state, vertex)){
                return true;
            }
        }
        return false;
    }

}
