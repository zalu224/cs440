package src.labs.zombayes.agents;


// SYSTEM IMPORTS
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
//stats
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;

// JAVA PROJECT IMPORTS
import edu.bu.labs.zombayes.agents.SurvivalAgent;
import edu.bu.labs.zombayes.features.Features.FeatureType;
import edu.bu.labs.zombayes.linalg.ElementWiseOperator;
import edu.bu.labs.zombayes.linalg.Matrix;
import edu.bu.labs.zombayes.utils.Pair;



public class NaiveBayesAgent
    extends SurvivalAgent
{

    public static class NaiveBayes
        extends Object
    {

        public static final FeatureType[] FEATURE_HEADER = {FeatureType.CONTINUOUS,
                                                            FeatureType.CONTINUOUS,
                                                            FeatureType.DISCRETE,
                                                            FeatureType.DISCRETE};
        
                                                            // For continuous features
        private Map<Integer, Double> classPriors;
        private Map<Integer, Map<Double, Double>> means;
        private Map<Integer, Map<Double, Double>> variances;
        private Map<Integer, Map<Double, Map<Double, Double>>> discreteProbabilities; // For discrete features
        private double numDataPoints; // class member variable to hold the number of data points


        // TODO: complete me!
        public NaiveBayes()
        {
            this.classPriors = new HashMap<>();
            this.means = new HashMap<>();
            this.variances = new HashMap<>();
            this.discreteProbabilities = new HashMap<>();
           
        }
        
        public void fit(Matrix X, Matrix y_gt) {
            try {
                // Create row masks for zombies (class label 1.0) and humans (class label 0.0)
                Matrix zombieRowMask = y_gt.getRowMaskEq(1.0, 0);
                Matrix humanRowMask = y_gt.getRowMaskEq(0.0, 0);
            
                // Filter rows from X for zombies and humans
                Matrix X_zombies = X.filterRows(zombieRowMask);
                Matrix X_humans = X.filterRows(humanRowMask);
            
                // Calculate class priors
                double numZombies = zombieRowMask.sum().item();
                double numHumans = humanRowMask.sum().item();
                this.numDataPoints = y_gt.numel();
            
                classPriors.put(1, numZombies / numDataPoints);
                classPriors.put(0, numHumans / numDataPoints);
            
                // Initialize mean and variance for continuous features
                // and count occurrences for discrete features for both zombies and humans
                // Note: Assuming X is structured such that continuous features come first
                int numFeatures = X.getShape().getNumCols();
                for (int j = 0; j < numFeatures; j++) {
                    FeatureType featureType = FEATURE_HEADER[j];
            
                    if (featureType == FeatureType.CONTINUOUS) {
                        // Compute mean and variance for feature j for zombies
                        Matrix featureColumnZombies = X_zombies.getCol(j);
                        double meanZombies = featureColumnZombies.sum().item() / numZombies;
            
                        // Compute mean and variance for feature j for humans
                        Matrix featureColumnHumans = X_humans.getCol(j);
                        double meanHumans = featureColumnHumans.sum().item() / numHumans;
                        
                        // Square the elements of the feature column for zombies and humans
                        Matrix squaredZombies = featureColumnZombies.emul(featureColumnZombies);
                        Matrix squaredHumans = featureColumnHumans.emul(featureColumnHumans);
            
                        // Compute the variance
                        double varianceZombies = squaredZombies.sum().item() / numZombies - meanZombies * meanZombies;
                        double varianceHumans = squaredHumans.sum().item() / numHumans - meanHumans * meanHumans;
            
                        // Store computed values
                        means.computeIfAbsent(j, k -> new HashMap<>());
                        means.get(j).put(1.0, meanZombies);
                        means.get(j).put(0.0, meanHumans);
            
                        variances.computeIfAbsent(j, k -> new HashMap<>());
                        variances.get(j).put(1.0, varianceZombies);
                        variances.get(j).put(0.0, varianceHumans);

                    } else { // FeatureType.DISCRETE
                        // Ensure the key is Double when using computeIfAbsent
                        for (int i = 0; i < numDataPoints; i++) {
                            double featureValue = X.get(i, j);
                            Double label = y_gt.get(i, 0); // Cast to Double instead of int

                            discreteProbabilities.computeIfAbsent(j, k -> new HashMap<>())
                                                .computeIfAbsent(label, k -> new HashMap<>())
                                                .merge(featureValue, 1.0, Double::sum);
                        }
            
                        // Convert counts to probabilities
                        for (Map.Entry<Double, Map<Double, Double>> entry : discreteProbabilities.get(j).entrySet()) {
                            double classLabel = entry.getKey();
                            Map<Double, Double> valueCounts = entry.getValue();
                            double total = classLabel == 1.0 ? numZombies : numHumans;
                            for (Map.Entry<Double, Double> valueEntry : valueCounts.entrySet()) {
                                valueEntry.setValue(valueEntry.getValue() / total);
                            }
                        }
                    }
                } 
            } catch (Exception e){
                e.printStackTrace();
            }
        
        }
        
    
        

        public int predict(Matrix x) {
            // Posteriors for each class
            double posteriorZombie = Math.log(classPriors.get(1));
            double posteriorHuman = Math.log(classPriors.get(0));

            // Loop over each feature
            for (int j = 0; j < x.getShape().getNumCols(); j++) {
                double featureValue = x.get(0, j); // Assuming x is a row vector
                FeatureType featureType = FEATURE_HEADER[j];
        
                if (featureType == FeatureType.CONTINUOUS) {
                    // Calculate the likelihood of the feature for zombies
                    double meanZombie = means.get(j).get(1.0);
                    double varianceZombie = variances.get(j).get(1.0);
                    double likelihoodZombie = Math.log(1 / Math.sqrt(2 * Math.PI * varianceZombie)) -
                                              Math.pow(featureValue - meanZombie, 2) / (2 * varianceZombie);
        
                    // Calculate the likelihood of the feature for humans
                    double meanHuman = means.get(j).get(0.0);
                    double varianceHuman = variances.get(j).get(0.0);
                    double likelihoodHuman = Math.log(1 / Math.sqrt(2 * Math.PI * varianceHuman)) -
                                              Math.pow(featureValue - meanHuman, 2) / (2 * varianceHuman);
        
                    // Add the log likelihood to the posterior
                    posteriorZombie += likelihoodZombie;
                    posteriorHuman += likelihoodHuman;
                } else { // FeatureType.DISCRETE
                    // Get the probability of the feature value for zombies and humans
                    double probabilityZombie = discreteProbabilities.get(j).get(1.0).getOrDefault(featureValue, 1.0 / (this.numDataPoints + 2)); // Laplace smoothing
                    double probabilityHuman = discreteProbabilities.get(j).get(0.0).getOrDefault(featureValue, 1.0 / (this.numDataPoints + 2)); // Laplace smoothing
        
                    // Add the log probability to the posterior
                    posteriorZombie += Math.log(probabilityZombie);
                    posteriorHuman += Math.log(probabilityHuman);
                }
            }
        
            // Return the class with the highest posterior probability
            return posteriorZombie > posteriorHuman ? 1 : 0;
        }
        
    
    }
    
    private NaiveBayes model;

    public NaiveBayesAgent(int playerNum, String[] args)
    {
        super(playerNum, args);
        this.model = new NaiveBayes();
    }

    public NaiveBayes getModel() { return this.model; }

    @Override
    public void train(Matrix X, Matrix y_gt)
    {
        System.out.println(X.getShape() + " " + y_gt.getShape());
        this.getModel().fit(X, y_gt);
    }

    @Override
    public int predict(Matrix featureRowVector)
    {
        return this.getModel().predict(featureRowVector);
    }

}
