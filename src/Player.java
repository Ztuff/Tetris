import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

/**
 * Created by nhan on 6/4/16.
 * This class is a player that will automatically spawn
 * different players that use a policy and evaluate that
 * policy based on the statistic
 *
 * Mean
 * Variance
 */
class Player implements Runnable {
    private double[] weights;
    private String policyFileName;
    private String reportFileName;
    private File policyFile;
    private File reportFile;

    private final double MIN_VAL = Double.NEGATIVE_INFINITY;
    private FeatureFunction ff = new FeatureFunction();

    private int score;
    private int gameLimit;

    private boolean inGame;

    private double[] getWeights() { return weights; }
    private int getScore() { return score; }

    public static final String REPORT_DIR = "Report";

    Player(String policyFile, int gameLimit) {
        this.policyFileName = policyFile;
        this.reportFileName = "report_" + policyFile;

        this.policyFile = new File(Learner.LEARNER_DIR, policyFile);
        this.reportFile = new File(REPORT_DIR, reportFileName);

        this.score = 0;
        this.weights = new double[FeatureFunction.NUM_OF_FEATURE];
        this.gameLimit = gameLimit;

        this.inGame = false;

        readPolicy();
    }

    /**
     * Reading the policy (weights vector) from the given file inside
     * Learner folder
     *
     */
    private void readPolicy() {
        try (Scanner sc = new Scanner(new FileReader(policyFile))) {
            for (int i = 0; i < weights.length; i++) {
                weights[i] = sc.nextDouble();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            // If anything wrong happens, this player will not play any game
            System.out.println("Something's wrong. Not playing");
            gameLimit = 0;
        }
    }

    /**
     * Write the report header to include information about the policy being used
     * and the time the file is first created.
     *
     * @param bw The writer that it tries to write to
     * @param p The player that it trying to write report.
     * @throws IOException
     */
    private static void writeReportHeader(BufferedWriter bw, Player p) throws IOException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        StringBuilder sb = new StringBuilder();

        sb.append("Result for playing of policy:\n");
        StringBuilder helper = new StringBuilder();
        for (double w: p.getWeights()) {
            helper.append(w).append(" ");
        }

        sb.append(helper.toString().trim()).append('\n');
        sb.append("Created at: ").append(dateFormat.format(new Date())).append('\n');
        sb.append("-----------------------------------\n");

        bw.write(sb.toString());
    }

    /**
     * Write the result to the report file name given. If the file does not exist yet,
     * it will create the file, write the header and append the result. Otherwise, it
     * will just append the result
     *
     * @param p The player that is trying to write the result
     * @param fileName The name of the report file that the result should be written to
     */
    private static synchronized void writeToReport(Player p, String fileName) {
        File f = new File(fileName);
        boolean exists = f.exists() && f.isFile();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, exists))){
            if (!exists) {
                writeReportHeader(bw, p);
            }

            String s = new Integer(p.getScore()).toString();
            bw.write(s);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write the result to the report file given. If the file does not exist yet,
     * it will create a header and append the result. Otherwise, it will just
     * append the result
     *
     * @param p The player that is trying to write the result
     * @param f The file that the result should be written to
     */
    private static synchronized void writeToReport(Player p, File f) {
        boolean exists = f.exists() && f.isFile();
        if (!exists) try {
            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, exists))){
            if (!exists) {
                writeReportHeader(bw, p);
            }

            String s = new Integer(p.getScore()).toString();
            bw.write(s);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a fresh new State object and start playing based on the given policy
     * until it loses.
     *
     * Store the score with the number of rows cleared.
     */
    private void play() {
        State s = new State();
        inGame = true;
        while(!s.hasLost()) {
            s.makeMove(pickBestMove(s, weights));
        }

        this.score = s.getRowsCleared();
        inGame = false;
    }

    @Override
    public void run() {
        int limit = 0;
        boolean infinite = gameLimit < 0;
        try {
            while (!Thread.currentThread().isInterrupted() && infinite || limit < gameLimit) {
                limit++;
                play();

                writeToReport(this, reportFileName);
            }
        } finally {
            if (!inGame) writeToReport(this, reportFileName);
        }
    }

    /**
     * Will parse the report file and return the PolicyResult object.
     *
     * @param fileName the name of the report file
     * @return the PolicyResult object contains weights and average value. Null if not successful
     *
     */
    public static PolicyResult getResult(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            Scanner sc = new Scanner(br);
            // For simplicity, assuming all report file has correct header
            sc.nextLine(); // Discard first line
            String[] wStrs = sc.nextLine().split(" ");
            double[] w = new double[FeatureFunction.NUM_OF_FEATURE];
            for (int i = 0; i < wStrs.length; i++) {
                w[i] = Double.parseDouble(wStrs[i]);
            }

            sc.nextLine(); sc.nextLine(); // Discard next 2 lines
            int count = 0;
            int sum = 0;

            while(sc.hasNextInt()) {
                sum += sc.nextInt();
                count++;
            }

            double average = ((double) (sum)) / count;

            return new PolicyResult(w, average);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private int pickBestMove(State s, double[] w) {
        int bestMove=0, currentMove;
        double bestValue = MIN_VAL, currentValue;
        NextState ns = new NextState();

        for (currentMove = 0; currentMove < s.legalMoves().length; currentMove++)
        {
            ns.copyState(s);
            ns.makeMove(currentMove);
            currentValue = ff.valueOfState(ns, w);
            if (currentValue > bestValue) {
                bestMove = currentMove;
                bestValue = currentValue;
            }
        }

        return bestMove;
    }

    /**
     * Allow parallel playing the same policy file.
     *
     * @param args The string array of arguments passed
     *             fileName: the name of the policy file. Default is ""
     *             numOfPlayers: the number of players to play. Default is 4
     *             limit: the number of game limit that the player should play. Default is infinitely
     */
    public static void main(String[] args) {
        String fileName = args.length >= 1 && args[0] != null ? args[0] : "";
        int numOfPlayers = args.length >= 2 && args[1] != null ? Integer.parseInt(args[1]) : 4;
        int limit = args.length >= 3 && args[2] != null ? Integer.parseInt(args[2]) : -1;

        final Thread[] threads = new Thread[numOfPlayers];
        for (int i = 0; i < numOfPlayers; i++) {
            threads[i] = new Thread(new Player(fileName, limit));
            threads[i].start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Interrupted! Shutting down all instances of players.");
                for (Thread t: threads) t.interrupt();
            }
        });

        try {
            for (Thread t: threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted! Shutting down all instances of players.");
            for (Thread t: threads) t.interrupt();
        }
    }
}
