package src.labs.infexf.agents;

// SYSTEM IMPORTS
import edu.bu.labs.infexf.agents.SpecOpsAgent;
import edu.bu.labs.infexf.distance.DistanceMetric;
import edu.bu.labs.infexf.graph.Vertex;
import edu.bu.labs.infexf.graph.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Stack;



import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;


// JAVA PROJECT IMPORTS


public class InfilExfilAgent
    extends SpecOpsAgent
{

    public InfilExfilAgent(int playerNum)
    {
        super(playerNum);
    }

    // if you want to get attack-radius of an enemy, you can do so through the enemy unit's UnitView
    // Every unit is constructed from an xml schema for that unit's type.
    // We can lookup the "range" of the unit using the following line of code (assuming we know the id):
    //     int attackRadius = state.getUnit(enemyUnitID).getTemplateView().getRange();
   

    // private float safetyScore(Vertex vertex, StateView state){
    //     // gets all enemy Unit Ids
    //     Set<Integer> enemyIds = getOtherEnemyUnitIDs();
    //     // keeps track of all the enemies and makes the dst the greatest depending on which enemy there
    //     float closestDist = 100;
    //     //runs through each enemy to check if it is in enemy territory
    //     for (Integer enemyUnitID: enemyIds){
            
    //         UnitView enemyUnit = state.getUnit(enemyUnitID);
    //         if (enemyUnit == null){
    //             break;
    //         }
    //         Vertex enemyVertex = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());
    //         int attackRadius = state.getUnit(enemyUnitID).getTemplateView().getRange();
            
    //         // plus 2 to ensure that you stay away from enemy
    //         float chebyDist = DistanceMetric.chebyshevDistance(vertex, enemyVertex);
    //         closestDist = Math.min(closestDist, chebyDist);
    //         if (chebyDist <= attackRadius+1){
    //             // System.out.println("Destination:" + vertex + " EnemyVertex:" + enemyVertex);
    //             return 100f;
    //         // } else if (isAdjacentToResource(vertex, state)){
    //         //     return 20f;
    //         } 
    //         // 1/x, x is distance, y is the danger level
    //     }
    //     // System.out.println("Dest:" +vertex);
    //     return (float)(1/closestDist);
    
    // }
    private float minEnemyDist(Vertex vertex, StateView state){
        float closestDist = 1000f;
        for (Integer enemyUnitID : this.getOtherEnemyUnitIDs()) {
            UnitView enemyUnit = state.getUnit(enemyUnitID);
            if (enemyUnit == null){
                break;
            }
            Vertex enemyPosition = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());
            float distanceToEnemy = DistanceMetric.chebyshevDistance(vertex, enemyPosition);
            closestDist = Math.min(closestDist, distanceToEnemy);
    
        }
        return closestDist;
    }
    @Override
    public float getEdgeWeight(Vertex src,
                               Vertex dst,
                               StateView state)
    {  
        float riskFactor = 0.0f;

        for (Integer enemyUnitID : this.getOtherEnemyUnitIDs()) {
            UnitView enemyUnit = state.getUnit(enemyUnitID);
            if (enemyUnit == null){
                break;
            }
            Vertex enemyPosition = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());
            float distanceToEnemy = DistanceMetric.chebyshevDistance(dst, enemyPosition);
            int attackRadius = state.getUnit(enemyUnitID).getTemplateView().getRange();
            
            if (distanceToEnemy <= attackRadius+2) {
                riskFactor += 1000; 
            }
        }
        float total = (float)(1 + (10000/(Math.pow(minEnemyDist(dst, state), 2))))+riskFactor;
        // if (total > 100.0){
        //     System.out.println("Vertex to Move : "  + getNextVertexToMoveTo());
        //     System.out.println("DST" + dst + " Edge Weight: " + total  );
        // }
        return total;

        // return safetyScore(dst, state);
    }
    // return true if enemy archer has stepped on planned path
    @Override
    public boolean shouldReplacePlan(StateView state)
    {
        // Stack<Vertex> curPlan = this.getCurrentPlan();

        // for (Vertex vertex : curPlan) {
        //     if (safetyScore(vertex, state) > 1) { 
        //         return true;
        //     }
        // }
        // return false;
        for (Vertex v : this.getCurrentPlan()) {
            int x = v.getXCoordinate();
            int y = v.getYCoordinate();
            if ((state.isUnitAt(x, y) && !atTownHall(state, v)) ||
                minEnemyDist(v, state) <= 3) {
                // If an enemy is within a certain distance threshold, consider replanning.
                // System.out.println();
                return true;
        
            }
            
        }
        System.out.println();
        return false; // If none of the checks triggered a replan, the current plan is still valid.
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
    // private boolean adjacentResources(Vertex vertex, StateView state){
    //     // checks to see if there are resources two chebychev distances away, returns true if there is
    //     // returns false if there isn't, use state.isResourceAt(verX,verY)
    // }
    
}
