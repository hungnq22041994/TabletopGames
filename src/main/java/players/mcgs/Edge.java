package players.mcgs;

import core.actions.AbstractAction;

public class Edge {
    String destination;
    ActionStats stats;

    public Edge(String destination, ActionStats stats) {
        this.destination = destination;
        this.stats = stats;
    }

    @Override
    public String toString() {
        return "(" + destination + ", " + stats + ")";
    }
}
