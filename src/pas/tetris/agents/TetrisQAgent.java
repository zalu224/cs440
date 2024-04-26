package src.pas.tetris.agents;


import java.util.ArrayList;
import java.util.HashMap;
// SYSTEM IMPORTS
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import edu.bu.battleship.game.Game;
// JAVA PROJECT IMPORTS
import edu.bu.tetris.agents.QAgent;
import edu.bu.tetris.agents.TrainerAgent.GameCounter;
import edu.bu.tetris.game.Block;
import edu.bu.tetris.game.Board;
import edu.bu.tetris.game.Game.GameView;
import edu.bu.tetris.game.minos.Mino;
import edu.bu.tetris.linalg.Matrix;
import edu.bu.tetris.nn.Model;
import edu.bu.tetris.nn.LossFunction;
import edu.bu.tetris.nn.Optimizer;
import edu.bu.tetris.nn.models.Sequential;
import edu.bu.tetris.nn.layers.Dense; // fully connected layer
import edu.bu.tetris.nn.layers.ReLU;  // some activations (below too)
import edu.bu.tetris.nn.layers.Tanh;
import edu.bu.tetris.nn.layers.Sigmoid;
import edu.bu.tetris.training.data.Dataset;
import edu.bu.tetris.utils.Pair;


public class TetrisQAgent
    extends QAgent
{

    
    private Random random;

    public TetrisQAgent(String name)
    {
        super(name);
        this.random = new Random(12345); // optional to have a seed
    }

    public Random getRandom() { return this.random; }

    @Override
    public Model initQFunction()
    {
        // build a single-hidden-layer feedforward network
        // this example will create a 3-layer neural network (1 hidden layer)
        // in this example, the input to the neural network is the
        // image of the board unrolled into a giant vector
        final int numPixelsInImage = Board.NUM_ROWS * Board.NUM_COLS;
        
        // // Adding 1 for the reward feature
        // final int inputVectorSize = numPixelsInImage + 4;  
        // final int hiddenDim = 2 * numPixelsInImage;
        // final int outDim = 1;

        // Sequential qFunction = new Sequential();

        // // Adjust input layer to new size
        // qFunction.add(new Dense(inputVectorSize, hiddenDim));  
        
        // qFunction.add(new Tanh());
        // qFunction.add(new Dense(hiddenDim, outDim));
        final int totalFeatures = numPixelsInImage + Board.NUM_COLS + 6; //
        // For the first hidden layer to have more neurons allows the network
        // to create a broad range of features combinations and interactions
        // from the input data  
        final int hiddenDim1 = totalFeatures * 2;
        // The second hidden layer then takes the broad interpretations from
        // the first hidden layer and hone them together
        final int hiddenDim2 = totalFeatures;
        final int outDim = 1;

        Sequential qFunction = new Sequential();

        qFunction.add(new Dense(totalFeatures, hiddenDim1));
        // Using ReLU to ensure good gradient flow, and to take care of the problem
        // of vanishing gradient
        qFunction.add(new ReLU()); 
        qFunction.add(new Dense(hiddenDim1, hiddenDim2));
        qFunction.add(new ReLU()); // Additional ReLU activation for deeper network
        qFunction.add(new Dense(hiddenDim2, outDim));

        return qFunction;
    }

    /**
        This function is for you to figure out what your features
        are. This should end up being a single row-vector, and the
        dimensions should be what your qfunction is expecting.
        One thing we can do is get the grayscale image
        where squares in the image are 0.0 if unoccupied, 0.5 if
        there is a "background" square (i.e. that square is occupied
        but it is not the current piece being placed), and 1.0 for
        any squares that the current piece is being considered for.
        
        We can then flatten this image to get a row-vector, but we
        can do more than this! Try to be creative: how can you measure the
        "state" of the game without relying on the pixels? If you were given
        a tetris game midway through play, what properties would you look for?
     */
    @Override
    public Matrix getQFunctionInput(final GameView game,
                                    final Mino potentialAction)
    {   
        List<Double> features = new ArrayList<Double>();
        Matrix resultingOneVectorMatrix = null;
        try
        {
            // vector consist of :
            //height(10), lines (clears), holes, blockades,  edge touching another block,
            // edge touching wall, edge touching floor
            // Calculate the board representation as a grayscale image
            Matrix grayscaleImage = game.getGrayscaleImage(potentialAction);

            // Flatten the grayscale image to get a base row-vector
            Matrix flattenedImage = grayscaleImage.flatten();

            for (int col = 0; col < flattenedImage.getShape().getNumCols(); col++){
                for(int row = 0; row < flattenedImage.getShape().getNumRows(); row++){
                    features.add(flattenedImage.get(row, col));
                }
            }

            // Explicitly provide the NN with the height of each column of the Game
            for (int col = 0; col < grayscaleImage.getShape().getNumCols(); col++) {
                double colHeight = 0.0;
                for (int row = 0; row < grayscaleImage.getShape().getNumRows(); row++) {
                    if (grayscaleImage.get(row, col) != 0) { // meaning not empty
                        colHeight = grayscaleImage.getShape().getNumRows() - row;
                        break;
                    }
                }
                features.add(colHeight);
            }

            // Evalute potential for line completions and add to features
            features.add(calculatePotentialLineCompletion(grayscaleImage));
             
            // add number of holes to matrix
            features.add(calculateHoles(grayscaleImage));
             
            // blockades added to features
            features.add(calculateBlockades(grayscaleImage));

            calculateEdgeScores(grayscaleImage);
            //add features of edge touching certain parts of the board
            features.add(edgeTouchBlockM);

            features.add(edgeTouchWallM);

            features.add(edgeTouchFloorM);

            // turn the feature List into a Vector
            Matrix zeroMatrix = Matrix.zeros(1, features.size());
 
            for (int row = 0; row < 1; row++) {
                 for (int col = 0; col < features.size(); col++) {
                     zeroMatrix.set(row, col, features.get(col));
                 }
            }
             
             
            resultingOneVectorMatrix = zeroMatrix;

        } catch(Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        //height of columns in game, number of line completions, 3 classified holes, bumpiness
        return resultingOneVectorMatrix;
    }

    private double calculateHoles(Matrix matrix) {
        int holes = 0;
        for (int col = 0; col < matrix.getShape().getNumCols(); ++col) {
            boolean blockFound = false;
            for (int row = 0; row < matrix.getShape().getNumRows(); ++row) {
                if (matrix.get(row, col) != 0) {
                    blockFound = true;
                } else if (blockFound) {
                    // If a block has been found and the current cell is empty, it's a hole
                    holes++;
                }
            }
        }
        return holes;
    }

    private double calculateBlockades(Matrix matrix){
        int blockades = 0;
        for (int col = 0; col < matrix.getShape().getNumCols(); ++col) {
            boolean holeFound = false;
            for (int row = 0; row < matrix.getShape().getNumRows(); ++row) {
                if (matrix.get(row, col) == 0) {
                    holeFound = true;
                } else if (holeFound) {
                    // If a hole has been found and the current cell is filled, it's a blockade
                    blockades++;
                }
            }
        }
        return blockades;
    }

    
    
    public double calculateReward(Matrix matrix) {
        //(-0.03*heightScore) - (7.5*holes) - (3.5*blockades) + 
        //(8.0*clears)+(3.0*edgeTBlock) + (2.5*edgeTWall) + (5.0*edgeTFloor)
        double weightHeight = -0.03;
        double weightLines = 8.0;
        double weightHoles = 7.5;
        double weightBlockades = 3.5;
        double weightETB = 3.0;
        double weightETW = 2.5;
        double weightETF = 5.0;

        // Initialize features
        double aggregateHeight = 0;
        double completeLines = 0;
        double holes = 0;
        double blockades = 0;
        double edgeToBlock = 0;
        double edgeToWall = 0;
        double edgeToFloor = 0;
    
        int matrixWidth = matrix.getShape().getNumCols();
        int matrixHeight = matrix.getShape().getNumRows();
        int[] columnHeights = new int[matrixWidth];
    
        // Compute heights
        for (int col = 0; col < matrixWidth; col++) {
            columnHeights[col] = 0; // Assume the column is empty
            for (int row = matrixHeight - 1; row >= 0; row--) {
                if (matrix.get(row, col) != 0.0) {
                    columnHeights[col] = row+1; // Record the height
                    break;
                }
            }
            aggregateHeight += columnHeights[col];
        }

        // compute holes
        holes = calculateHoles(matrix);
    
        // Count complete lines clears
        completeLines = calculatePotentialLineCompletion(matrix);

        // blockades
        blockades = calculateBlockades(matrix);
    
        calculateEdgeScores(matrix);
        // Calculate edgeToBlock, edgeToWall, edgeToFloor
        edgeToBlock = edgeTouchBlockM;
        edgeToWall = edgeTouchWallM;
        edgeToFloor = edgeTouchFloorM;
        
    
        // Calculate reward using the fitness function
        double reward = (weightHeight*aggregateHeight) - (weightHoles*holes) - (weightBlockades*blockades) + 
        (weightLines*completeLines)+(weightETB*edgeToBlock) + (weightETW*edgeToWall) + (weightETF*edgeToFloor);
    
        return reward;
    }

    
   
    public double calculateReward(Board board) {
        // Weights for different board features
        double weightHeight = -0.03;
        double weightLines = 8.0;
        double weightHoles = -7.5;
        double weightBlockades = -3.5;
        double weightETB = 3.0;  // Edge Touching Block
        double weightETW = 2.5;  // Edge Touching Wall
        double weightETF = 5.0;  // Edge Touching Floor

        // Calculate board features
        double aggregateHeight = calculateSumOfHeights(board);
        double completeLines = calculateClears(board);
        double holes = calculateHoles(board);
        double blockades = calculateBlockades(board);
        calculateEdgeScores(board);
        double edgeToBlock = edgeTouchBlock;
        double edgeToWall = edgeTouchWall;
        double edgeToFloor = edgeTouchFloor;

        // Calculate reward using the fitness function
        double reward = (weightHeight * aggregateHeight) - (weightHoles * holes) - (weightBlockades * blockades) +
                        (weightLines * completeLines) + (weightETB * edgeToBlock) + (weightETW * edgeToWall) + (weightETF * edgeToFloor);

        return reward;
    }

    /**
     * This method is used to decide if we should follow our current policy
     * (i.e. our q-function), or if we should ignore it and take a random action
     * (i.e. explore).
     *
     * Remember, as the q-function learns, it will start to predict the same "good" actions
     * over and over again. This can prevent us from discovering new, potentially even
     * better states, which we want to do! So, sometimes we should ignore our policy
     * and explore to gain novel experiences.
     *
     * The current implementation chooses to ignore the current policy around 5% of the time.
     * While this strategy is easy to implement, it often doesn't perform well and is
     * really sensitive to the EXPLORATION_PROB. I would recommend devising your own
     * strategy here.
     */
    @Override
    public boolean shouldExplore(final GameView game,
                                 final GameCounter gameCounter)
    {
        long totalGamesPlayed = gameCounter.getTotalGamesPlayed();
        // Adjust the exploration rate based on the number of games played
        double currentExplorationProb = Math.max(MIN_EXPLORATION_PROB, 
            INITIAL_EXPLORATION_PROB * Math.pow(EXPLORATION_DECAY_RATE, totalGamesPlayed));
        return this.getRandom().nextDouble() <= currentExplorationProb;

        // return this.getRandom().nextDouble() <= EXPLORATION_PROB;
    }
    private double MIN_EXPLORATION_PROB = 0.05; //init %5
    private double INITIAL_EXPLORATION_PROB = 1.0;
    private double EXPLORATION_DECAY_RATE = 0.995; // Adjust based on the desired decay rate

    /**
     * This method is a counterpart to the "shouldExplore" method. Whenever we decide
     * that we should ignore our policy, we now have to actually choose an action.
     *
     * You should come up with a way of choosing an action so that the model gets
     * to experience something new. The current implemention just chooses a random
     * option, which in practice doesn't work as well as a more guided strategy.
     * I would recommend devising your own strategy here.
     */

    HashMap<Mino, Double> minoToReward = new HashMap<Mino, Double>();
    HashMap<Mino, Integer> minoToCount = new HashMap<Mino, Integer>();
    int totalMinoCount = 0;
    // Upper Confidence Bound (UCB) parameters
    private static final double C = Math.sqrt(2);
    private static final int NE = 10; // Threshold for "seen this action-state pair enough times"

    public Mino getExplorationMove(final GameView game) {
        List<Mino> possibleActions = game.getFinalMinoPositions();
        double R_plus = calculateOptimisticRewardEstimate(game); // This is the optimistic estimate
        
        Mino bestAction = null;
        double bestValue = Double.NEGATIVE_INFINITY;
    
        for (Mino action : possibleActions) {
            double u = minoToReward.getOrDefault(action, R_plus); // Use R_plus for initial rewards
            int n = minoToCount.getOrDefault(action, 0);
            double explorationValue = u + C * Math.sqrt(Math.log(totalMinoCount + 1) / (n + 1));
            
            if (explorationValue > bestValue) {
                bestValue = explorationValue;
                bestAction = action;
            }
        }
    
        if (bestAction == null) {
            int randomIndex = this.getRandom().nextInt(possibleActions.size());
            bestAction = possibleActions.get(randomIndex);
        }
    
        // Update the mino count and rewards for the selected action
        updateActionRewardAndCount(bestAction, game);
    
        return bestAction;
    }
    
    private double calculateOptimisticRewardEstimate(GameView game) {
        // Define optimistic values for features
        double optimisticHeight = 0; // Ideally, the stack is low
        double optimisticLines = Board.NUM_ROWS; // Optimistically, all lines could be cleared
        double optimisticHoles = 0; // No holes optimistically
        double optimisticBlockades = 0; // No blockades optimistically
        double optimisticEdgeTBlock = Board.NUM_ROWS * Board.NUM_COLS; // Max edges touching blocks
        double optimisticEdgeTWall = 2 * (Board.NUM_ROWS + Board.NUM_COLS); // All edges touching walls
        double optimisticEdgeTFloor = Board.NUM_COLS; // All columns have edges touching the floor
    
        // Calculate R+ based on the weighted sum of optimistic estimates
        double R_plus = calculateRewardFromFeatures(
            optimisticHeight,
            optimisticLines,
            optimisticHoles,
            optimisticBlockades,
            optimisticEdgeTBlock,
            optimisticEdgeTWall,
            optimisticEdgeTFloor
        );
    
        return R_plus;
    }
    
    private double calculateRewardFromFeatures(
        double height, double lines, double holes, double blockades,
        double edgeTBlock, double edgeTWall, double edgeTFloor
    ) {
        
        // Use the weights from your reward function
        double reward = (-0.03 * height) - (7.5 * holes) - (3.5 * blockades) + 
                (8.0 * lines) + (3.0 * edgeTBlock) + (2.5 * edgeTWall) + (5.0 * edgeTFloor);
        return reward;
    }
    
    private void updateActionRewardAndCount(Mino action, GameView game) {
        // copy board state
        Board boardCopy = new Board(game.getBoard());

        // Add the Mino to the copied board to simulate the action
        boardCopy.addMino(action);
        
        // Calculate the reward based on the new state of the board after the action
        double rewardAfterAction = calculateReward(boardCopy);

        // Update the cumulative reward for this action
        double totalReward = minoToReward.getOrDefault(action, 0.0) + rewardAfterAction;
        minoToReward.put(action, totalReward);

        // Update the count of how many times the action has been taken
        int actionCount = minoToCount.getOrDefault(action, 0) + 1;
        minoToCount.put(action, actionCount);

        // Increment the total count of actions taken
        totalMinoCount += 1;
    }
    // we could for one use getReward to examine the current board, 
    // or i can keep a hashmap of each mino with its count
    // private static final double UCBTunabilityFactor = Math.sqrt(2);

    // private double getUCBScore(double minoReward, int minoCount, int totalMinoCount) {
    //     double avgMinoReward = minoReward / minoCount;
    //     return avgMinoReward + UCBTunabilityFactor * Math.sqrt(Math.log(totalMinoCount) / minoCount);
    // }
    // we could for one use getReward to examine the current board, 
    // or i can keep a hashmap of each mino with its count
    // private static final double UCBTunabilityFactor = Math.sqrt(2);

    // private double getUCBScore(double minoReward, int minoCount, int totalMinoCount) {
    //     double avgMinoReward = minoReward / minoCount;
    //     return avgMinoReward + UCBTunabilityFactor * Math.sqrt(Math.log(totalMinoCount) / minoCount);
    // }

    // private double getRewardForMino(final GameView game, Mino mino) {
    //     double score = 0.0;
    //     try {
    //         Matrix grayScaleMatrix = game.getGrayscaleImage(mino);
    //         score = calculateReward(grayScaleMatrix);

    //     } catch (Exception err) {
    //         System.err.println("Failed to generate grayscale image: " + err.getMessage());
    //         err.printStackTrace();

    //     }

    //     if (score == 0.0) {
    //         throw new IllegalArgumentException("The method getRewardForMinoInMatrix()" + 
    //         "returned a score of 0.0, which should not be allowed.");
    //     }

    //     return score;
    // }

    // private double expNegLogX(double x) {
    //     if (x <= 0) {
    //         throw new IllegalArgumentException("x must be greater than 0");
    //     }
    //     return Math.exp(-Math.log(x));
    // }

    /**
     * This method is called by the TrainerAgent after we have played enough training games.
     * In between the training section and the evaluation section of a phase, we need to use
     * the exprience we've collected (from the training games) to improve the q-function.
     *
     * You don't really need to change this method unless you want to. All that happens
     * is that we will use the experiences currently stored in the replay buffer to update
     * our model. Updates (i.e. gradient descent updates) will be applied per minibatch
     * (i.e. a subset of the entire dataset) rather than in a vanilla gradient descent manner
     * (i.e. all at once)...this often works better and is an active area of research.
     *
     * Each pass through the data is called an epoch, and we will perform "numUpdates" amount
     * of epochs in between the training and eval sections of each phase.
     */
    @Override
    public void trainQFunction(Dataset dataset,
                               LossFunction lossFunction,
                               Optimizer optimizer,
                               long numUpdates)
    {
        for(int epochIdx = 0; epochIdx < numUpdates; ++epochIdx)
        {
            dataset.shuffle();
            Iterator<Pair<Matrix, Matrix> > batchIterator = dataset.iterator();

            while(batchIterator.hasNext())
            {
                Pair<Matrix, Matrix> batch = batchIterator.next();

                try
                {
                    Matrix YHat = this.getQFunction().forward(batch.getFirst());

                    optimizer.reset();
                    this.getQFunction().backwards(batch.getFirst(),
                                                  lossFunction.backwards(YHat, batch.getSecond()));
                    optimizer.step();
                } catch(Exception e)
                {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * This method is where you will devise your own reward signal. Remember, the larger
     * the number, the more "pleasurable" it is to the model, and the smaller the number,
     * the more "painful" to the model.
     *
     * This is where you get to tell the model how "good" or "bad" the game is.
     * Since you earn points in this game, the reward should probably be influenced by the
     * points, however this is not all. In fact, just using the points earned this turn
     * is a **terrible** reward function, because earning points is hard!!
     *
     * I would recommend you to consider other ways of measuring "good"ness and "bad"ness
     * of the game. For instance, the higher the stack of minos gets....generally the worse
     * (unless you have a long hole waiting for an I-block). When you design a reward
     * signal that is less sparse, you should see your model optimize this reward over time.
     */
    @Override
    public double getReward(final GameView game)
    {   
        //height, lines (clears), holes, blockades,  edge touching another block,
         // edge touching wall, edge touching floor
        Board board = game.getBoard();
        double heightScore = calculateSumOfHeights(board);

        double clears = calculateClears(board);

        double holes = calculateHoles(board);
        
        double blockades = calculateBlockades(board);

        double score = calculateEdgeScores(board);
        
        double reward = (-0.03*heightScore) - (7.5*holes) - (3.5*blockades) + 
            (8.0*clears)+score;

        return reward;
        // return game.getScoreThisTurn();
    }

    private double calculatePotentialLineCompletion(Matrix matrix) {
        double completeLines = 0.0;
        for (int row = 0; row < matrix.getShape().getNumRows(); row++) {
            boolean complete = true;
            for (int col = 0; col < matrix.getShape().getNumCols(); col++) {
                if (matrix.get(row, col) == 0) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                completeLines++;
            }
        }
        return completeLines;
    }

    // private double calculateHeightScore(Board board) {
    //     final int maxRows = Board.NUM_ROWS;
    //     double totalHeight = 0;
    //     double[] heights = new double[board.NUM_COLS];  // Store individual column heights
    //     double maxColumnHeight = 0;

    //     // Calculate total filled height and find max height
    //     for (int col = 0; col < board.NUM_COLS; col++) {
    //         for (int row = 0; row < board.NUM_ROWS; row++) {
    //             if (board.isCoordinateOccupied(col, row)) {
    //                 heights[col] = maxRows - row;
    //                 if (heights[col] > maxColumnHeight) {
    //                     maxColumnHeight = heights[col];
    //                 }
    //                 break;
    //             }
    //         }
    //         totalHeight += heights[col];
    //     }

    //     // Apply exponential decay formula with variance
    //     return Math.exp(1 - (0.15 * maxColumnHeight));
    // }

    // private double calculateBumpiness(Matrix matrix){
    //     double bumpiness = 0.0;

    //     int matrixWidth = matrix.getShape().getNumCols();
    //     int matrixHeight = matrix.getShape().getNumRows();
    //     int[] columnHeights = new int[matrixWidth];
    
    //     // Compute heights
    //     for (int col = 0; col < matrixWidth; col++) {
    //         columnHeights[col] = 0; // Assume the column is empty
    //         for (int row = matrixHeight - 1; row >= 0; row--) {
    //             if (matrix.get(row, col) != 0.0) {
    //                 columnHeights[col] = row; // Record the height
    //                 break;
    //             }
    //         }
    //     }
    //     // Calculate bumpiness
    //     for (int col = 0; col < matrixWidth - 1; col++) {
    //         bumpiness += Math.abs(columnHeights[col] - columnHeights[col + 1]);
    //     }
    //     return bumpiness;

    // }
    /* a = -0.798752914564018
            b = 0.522287506868767
            c = -0.24921408023878
             d = -0.164626498034284
        field_value = Height * a + Complete_lines * b + Holes * c + Bumpiness * d
    */
    // private double calculateReward(Board board) {
    //     double weightHeight = -0.798752914564018;
    //     double weightLines =  0.522287506868767;
    //     double weightHoles = -0.24921408023878;
    //     double weightBumpiness = -0.164626498034284; 

    //     // double weightHeight = -0.51;
    //     // double weightLines = 0.76;
    //     // double weightHoles = -0.36;
    //     // double weightBumpiness = -0.18;
    
    //     // Initialize features
    //     double aggregateHeight = 0;
    //     double completeLines = 0;
    //     double holes = 0;
    //     double bumpiness = 0;
    
    //     int[] columnHeights = new int[board.NUM_COLS];
    
       
    
    //     // Calculate reward using the fitness function
    //     double reward = weightHeight * aggregateHeight +
    //                     weightLines * completeLines +
    //                     weightHoles * holes +
    //                     weightBumpiness * bumpiness;
    
    //     return reward;
    // }

    private List<Integer> getFullLines(Board board) {
        List<Integer> fullLines = new ArrayList<>();
        boolean isLineFull;
    
        // Loop through every row from the bottom to the top of the board
        for (int row = 0; row < Board.NUM_ROWS; row++) {
            isLineFull = true; // Assume the line is full until proven otherwise
    
            // Check each column in the current row
            for (int col = 0; col < Board.NUM_COLS; col++) {
                if (!board.isCoordinateOccupied(col, row)) {
                    // If any column is not occupied, the line is not full
                    isLineFull = false;
                    break;
                }
            }
    
            // If after checking all columns, the line is full, add it to the list
            if (isLineFull) {
                fullLines.add(row);
            }
        }
    
        return fullLines;
    }

    private double calculateHoles(Board board) {
        int holes = 0;
        for (int col = 0; col < board.NUM_COLS; ++col) {
           boolean blockFound = false;
           for (int row = 0; row < board.NUM_ROWS; ++row) {
                if (board.isCoordinateOccupied(col, row)) {
                    blockFound = true;
                } else if (blockFound) {
                    holes++;
                }
            }
        }
        return holes;
    }
  
    private double calculateBlockades(Board board) {
        int blockades = 0;
        for (int col = 0; col < board.NUM_COLS; ++col) {
           boolean holeFound = false;
           for (int row = 0; row < board.NUM_ROWS; ++row) {
              if (!board.isCoordinateOccupied(col, row)) {
                 holeFound = true;
              } else if (holeFound) {
                 blockades++;
              }
           }
        }
        return blockades;
    }
  
    private double calculateClears(Board board) {
        List<Integer> fullLines = getFullLines(board);
        return fullLines.size();
    }
  
    private double calculateSumOfHeights(Board board) {
        int sumOfHeights = 0;
        for (int col = 0; col < board.NUM_COLS; ++col) {
           for (int row = 0; row < board.NUM_ROWS; ++row) {
              if (board.isCoordinateOccupied(col, row)) {
                 sumOfHeights += (board.NUM_ROWS - row);
                 break;
              }
           }
        }
        return sumOfHeights;
    }

    public double edgeTouchBlock;  
    public double edgeTouchWall;
    public double edgeTouchFloor;
    
    private double calculateEdgeScores(Board board) {
        double score = 0.0;
        edgeTouchBlock = 0.0;
        edgeTouchWall = 0.0;
        edgeTouchFloor = 0.0;
        for (int row = 0; row < board.NUM_ROWS; ++row) {
           for (int col = 0; col < board.NUM_COLS; ++col) {
              // Check if there is a block at this position
              if (board.isCoordinateOccupied(col, row)) {
                 // Check for block to the left
                 if (col > 0 && board.isCoordinateOccupied(col - 1, row)) {
                    score += 3.0;
                    edgeTouchBlock += 1.0;
                 }
                 // Check for block to the right
                 if (col < board.NUM_COLS - 1 && board.isCoordinateOccupied(col + 1, row)) {
                    score += 3.0;
                    edgeTouchBlock += 1.0;
                 }
                 // Check for block below
                 if (row < board.NUM_ROWS - 1 && board.isCoordinateOccupied(col, row + 1)) {
                    score += 3.0;
                    edgeTouchBlock += 1.0;
                 }
  
                 // Check for wall on the left
                 if (col == 0) {
                    score += 2.5;
                    edgeTouchWall += 1.0;
                 }
                 // Check for wall on the right
                 if (col == board.NUM_COLS - 1) {
                    score += 2.5;
                    edgeTouchWall += 1.0;
                 }
                 // Check for floor
                 if (row == board.NUM_ROWS - 1) {
                    score += 5.0;
                    edgeTouchFloor += 1.0;
                 }
              }
           }
        }
        
        return score;
    
    }
    

    public double edgeTouchBlockM;
    public double edgeTouchWallM;
    public double edgeTouchFloorM;

    public double calculateEdgeScores(Matrix matrix) {
        double score = 0.0;
        edgeTouchBlock = 0.0;
        edgeTouchWall = 0.0;
        edgeTouchFloor = 0.0;

        int numRows = matrix.getShape().getNumRows();
        int numCols = matrix.getShape().getNumCols();

        for (int row = 0; row < numRows; ++row) {
            for (int col = 0; col < numCols; ++col) {
                // Check if there is a block at this position
                if (matrix.get(row, col) != 0.0) {
                    // Check for block to the left
                    if (col > 0 && matrix.get(row, col-1) != 0.0) {
                        score += 3.0;
                        edgeTouchBlock += 1.0;
                    }
                    // Check for block to the right
                    if (col < numCols - 1 && matrix.get(row, col + 1) != 0.0) {
                        score += 3.0;
                        edgeTouchBlock += 1.0;
                    }
                    // Check for block below
                    if (row < numRows - 1 && matrix.get(row + 1, col) != 0.0) {
                        score += 3.0;
                        edgeTouchBlock += 1.0;
                    }

                    // Check for wall on the left
                    if (col == 0) {
                        score += 2.5;
                        edgeTouchWall += 1.0;
                    }
                    // Check for wall on the right
                    if (col == numCols - 1) {
                        score += 2.5;
                        edgeTouchWall += 1.0;
                    }
                    // Check for floor
                    if (row == numRows - 1) {
                        score += 5.0;
                        edgeTouchFloor += 1.0;
                    }
                }
            }
        }

        return score;
    }


    
          

    // private static final int a = 100;

    // private double aOverSqrtX(double x) {
    //     if (x < 0) {
    //         throw new IllegalArgumentException("x cannot be negative");
    //     }
    //     if (x == 0.0) {
    //         return 0.0;
    //     }
    //     return a / (Math.sqrt(x));
    // }

    // private double getLineCompleteScore(double scoreThisTurn) {
    //     return Math.exp(3.0 / 4.0 * scoreThisTurn) - 1;
    // }
}
