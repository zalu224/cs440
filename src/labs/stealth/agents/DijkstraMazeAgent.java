package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;                           // Directions in Sepia


import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue; // heap in java
import java.util.Set;
import java.util.Stack; 


// JAVA PROJECT IMPORTS


public class DijkstraMazeAgent
    extends MazeAgent
{

    public DijkstraMazeAgent(int playerNum)
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
        return (x >= 0 && x < state.getXExtent() && y >= 0 && y < state.getYExtent()) && (!state.isResourceAt(x,y)) 
            && (!state.isUnitAt(x, y));
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
            return new Path(src);
        }
		// Use a priority queue to order paths by their true cost
        PriorityQueue<Path> priorityQueue = new PriorityQueue<>(Comparator.comparing(Path::getTrueCost));
        // lowest cost would be retrieved first
        Map<Vertex, Float> shortestDistances = new HashMap<>(); //trades runtime for memory, memory cheap don't care
        shortestDistances.put(src, 0.0f);
        priorityQueue.add(new Path(src, 0f, -1f, null)); // Initialize with src
        //System.out.println("src" + src);
        //System.out.println("goal "+ goal);
        while (!priorityQueue.isEmpty()) {
            Path currentPath = priorityQueue.poll();
            Vertex currentVertex = currentPath.getDestination();

            if (isAdjacent(currentVertex, goal)) {
                return currentPath; // Found the goal, return the path
            }

            for (Vertex adjacent : getAdjacentVertices(currentVertex, state)) {
                float edgeCost = getEdgeWeight(currentVertex, adjacent);
                float newDistance = currentPath.getTrueCost() + edgeCost;
                if (!shortestDistances.containsKey(adjacent) || newDistance < shortestDistances.get(adjacent)) {
                    shortestDistances.put(adjacent, newDistance);
                    Path newPath = new Path(adjacent, edgeCost, newDistance, currentPath);
                    priorityQueue.add(newPath);
                }
            }
        }
        return null;
    }
    private float getEdgeWeight(Vertex v1, Vertex v2){
        float edgeCost = 0f;
        float northCost = 10f;
        float southCost = 1f;
        float eastCost = 5f;
        float westCost = 5f;
        if (getDirectionToMoveTo(v1, v2) == Direction.NORTHWEST){
            edgeCost = (float)Math.sqrt(Math.pow(northCost, 2) + Math.pow(westCost, 2));
        } else if (getDirectionToMoveTo(v1, v2) == Direction.NORTHEAST){
            edgeCost = (float)Math.sqrt(Math.pow(northCost, 2) + Math.pow(eastCost, 2));
        } else if (getDirectionToMoveTo(v1, v2) == Direction.NORTH) {
            edgeCost = northCost;
        } else if (getDirectionToMoveTo(v1, v2) == Direction.SOUTHWEST){
            edgeCost = (float)Math.sqrt(Math.pow(southCost, 2) + Math.pow(westCost, 2));
        } else if (getDirectionToMoveTo(v1, v2) == Direction.SOUTHEAST){
            edgeCost = (float)Math.sqrt(Math.pow(southCost, 2) + Math.pow(eastCost, 2));
        } else if (getDirectionToMoveTo(v1, v2) == Direction.SOUTH){
            edgeCost = southCost;
        } else if (getDirectionToMoveTo(v1, v2) == Direction.WEST){
            edgeCost = westCost;
        } else if (getDirectionToMoveTo(v1, v2) == Direction.EAST){
            edgeCost = eastCost;
        }
        return edgeCost;
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

    private boolean atTownHall(StateView state, Vertex vertex){
        UnitView unit = state.getUnit(state.unitAt(vertex.getXCoordinate(), vertex.getYCoordinate()));
        String unitName = unit.getTemplateView().getName();
        if (unitName.equals("TownHall")){
            return true;
        } else {
            return false;
        }
    }
}
