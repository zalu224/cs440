package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

import java.util.HashSet;   // will need for dfs
import java.util.Stack;     // will need for dfs
import java.util.Set;       // will need for dfs


// JAVA PROJECT IMPORTS


public class DFSMazeAgent
    extends MazeAgent
{

    public DFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }
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

    @Override
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {
        if (src.equals(goal)) {
            return new Path(src); // Path with only the source vertex if source is the goal
        }

        Stack<Path> stack = new Stack<>(); //stack because dfs and need to backwards in time after discovering
        Set<Vertex> visited = new HashSet<>();
        // System.out.println("src: " + src);
        // System.out.println("goal: " + goal);
        visited.add(src);
        stack.push(new Path(src)); 

        while (!stack.isEmpty()) {
            Path currentPath = stack.pop(); // returns the top of the stack
            Vertex currentVertex = currentPath.getDestination();
            // System.out.println("cur vertex: "+ currentVertex);
            // System.out.println("cur path:" + currentPath);
            for (Vertex adjacent : getAdjacentVertices(currentVertex, state)) {
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Path newPath = new Path(adjacent, 1.0f, currentPath);

                    if (isAdjacent(adjacent, goal)) {
                        return newPath; // Found the goal, return the path
                    }

                    stack.push(newPath);
                }
            }
        }
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
