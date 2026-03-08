package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SankeyDataVO {
    private List<Node> nodes;
    private List<Link> links;

    @Data
    public static class Node {
        private String name;
        private Integer depth;
        public Node(String name, int depth) { this.name = name; this.depth = depth; }
    }

    @Data
    public static class Link {
        private String source;
        private String target;
        private BigDecimal value;
        public Link(String source, String target, BigDecimal value) {
            this.source = source;
            this.target = target;
            this.value = value;
        }
    }
}
