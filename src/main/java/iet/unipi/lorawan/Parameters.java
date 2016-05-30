package iet.unipi.lorawan;

/**
 * Parameters of teh experiment
 */


final public class Parameters {
    public final boolean plot;
    public final int[] lengths;
    public final int[] codingRates;
    public final int[] txPowers;
    public final int[] dataRates;
    public final int repetitions;

    public Parameters(boolean plot, int[] lengths, int[] codingRates, int[] txPowers, int[] dataRates, int repetitions) {
        this.plot = plot;
        this.lengths = lengths;
        this.codingRates = codingRates;
        this.txPowers = txPowers;
        this.dataRates = dataRates;
        this.repetitions = repetitions;
    }
}
