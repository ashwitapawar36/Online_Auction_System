import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LoadBalancerService {

    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        LEAST_CONNECTIONS
    }

    private static final List<Node> NODES = new ArrayList<>(List.of(
            new Node(1, "Server 1", "ACTIVE", 0, 0),
            new Node(2, "Server 2", "ACTIVE", 0, 0),
            new Node(3, "Server 3", "ACTIVE", 0, 0)
    ));

    private static final AtomicInteger ROUND_ROBIN_INDEX = new AtomicInteger(0);
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);
    private static final ScheduledExecutorService REQUEST_COMPLETIONS = Executors.newScheduledThreadPool(3, task -> {
        Thread thread = new Thread(task, "load-balancer-request-completion");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile LoadBalancingStrategy strategy = LoadBalancingStrategy.ROUND_ROBIN;

    public static String getStrategy() {
        return strategy.name();
    }

    public static String setStrategy(String value) {
        String normalized = value == null ? "ROUND_ROBIN" : value.trim().toUpperCase();

        if ("ROUND_ROBIN".equals(normalized)) {
            strategy = LoadBalancingStrategy.ROUND_ROBIN;
        } else if ("LEAST_CONNECTIONS".equals(normalized)) {
            strategy = LoadBalancingStrategy.LEAST_CONNECTIONS;
        } else {
            strategy = LoadBalancingStrategy.ROUND_ROBIN;
        }

        return strategy.name();
    }

    public static String getState() {
        StringBuilder json = new StringBuilder("{\"strategy\":\"")
                .append(escape(strategy.name()))
                .append("\",\"nodes\":[");

        synchronized (NODES) {
            for (int i = 0; i < NODES.size(); i++) {
                Node node = NODES.get(i);

                if (i > 0) {
                    json.append(",");
                }

                json.append("{")
                        .append("\"serverId\":").append(node.serverId)
                        .append(",\"serverName\":\"").append(escape(node.serverName)).append("\"")
                        .append(",\"status\":\"").append(escape(node.status)).append("\"")
                        .append(",\"currentLoad\":").append(node.currentLoad)
                        .append(",\"totalRequests\":").append(node.totalRequests)
                        .append("}");
            }
        }

        return json.append("]}").toString();
    }

    public static String routeRequest(String incomingStrategy) {
        String selectedStrategy = setStrategy(incomingStrategy);
        Node selected;
        int simulatedLatencyMs = ThreadLocalRandom.current().nextInt(650, 1451);
        long requestId = REQUEST_ID.incrementAndGet();
        int activeLoadAtDispatch;
        int totalRequestsAtDispatch;

        synchronized (NODES) {
            selected = selectNode(selectedStrategy);
            selected.currentLoad++;
            selected.totalRequests++;
            activeLoadAtDispatch = selected.currentLoad;
            totalRequestsAtDispatch = selected.totalRequests;
        }

        Node completedNode = selected;
        REQUEST_COMPLETIONS.schedule(() -> {
            synchronized (NODES) {
                completedNode.currentLoad = Math.max(0, completedNode.currentLoad - 1);
            }
        }, simulatedLatencyMs, TimeUnit.MILLISECONDS);

        return "{"
                + "\"strategy\":\"" + escape(selectedStrategy) + "\","
                + "\"requestId\":" + requestId + ","
                + "\"serverId\":" + selected.serverId + ","
                + "\"serverName\":\"" + escape(selected.serverName) + "\","
                + "\"simulatedLatencyMs\":" + simulatedLatencyMs + ","
                + "\"currentLoad\":" + activeLoadAtDispatch + ","
                + "\"totalRequests\":" + totalRequestsAtDispatch + ""
                + "}";
    }

    public static String reset() {
        synchronized (NODES) {
            for (Node node : NODES) {
                node.currentLoad = 0;
                node.totalRequests = 0;
                node.status = "ACTIVE";
            }
            ROUND_ROBIN_INDEX.set(0);
            strategy = LoadBalancingStrategy.ROUND_ROBIN;
        }

        return "{\"message\":\"Load balancer reset successfully.\"}";
    }

    private static Node selectNode(String selectedStrategy) {
        if ("LEAST_CONNECTIONS".equals(selectedStrategy)) {
            int lowestLoad = NODES.stream().mapToInt(node -> node.currentLoad).min().orElse(0);
            List<Node> candidates = new ArrayList<>();
            for (Node node : NODES) {
                if (node.currentLoad == lowestLoad) {
                    candidates.add(node);
                }
            }
            return candidates.get(ROUND_ROBIN_INDEX.getAndUpdate(v -> (v + 1) % candidates.size()) % candidates.size());
        }

        return NODES.get(ROUND_ROBIN_INDEX.getAndUpdate(v -> (v + 1) % NODES.size()));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static class Node {
        private final int serverId;
        private final String serverName;
        private String status;
        private int currentLoad;
        private int totalRequests;

        private Node(int serverId, String serverName, String status, int currentLoad, int totalRequests) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.status = status;
            this.currentLoad = currentLoad;
            this.totalRequests = totalRequests;
        }
    }
}
