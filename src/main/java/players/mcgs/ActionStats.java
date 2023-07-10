package players.mcgs;

public class ActionStats {
    // Total value of this node
    public double totValue;
    // Number of visits
    public int nVisits;

    public ActionStats() {
        totValue = 0;
        nVisits = 0;
    }

    public ActionStats(double totValue, int nVisits) {
        this.totValue = totValue;
        this.nVisits = nVisits;
    }

    public double getTotValue() {
        return totValue;
    }

    public void setTotValue(double totValue) {
        this.totValue = totValue;
    }

    public int getnVisits() {
        return nVisits;
    }

    public void setnVisits(int nVisits) {
        this.nVisits = nVisits;
    }
}
