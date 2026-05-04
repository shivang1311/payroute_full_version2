package com.payroute.routing.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.routing.dto.request.RouteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluator {

    private final ObjectMapper objectMapper;

    /**
     * Evaluates a condition JSON string against a route request.
     * Supports simple conditions: {"field": "amount", "op": "gte", "value": 200000}
     * Supports composite conditions: {"and": [...]} or {"or": [...]}
     * Supported operators: eq, neq, gt, gte, lt, lte, in, contains
     * Supported fields: amount, currency, channel, purpose_code
     */
    public boolean evaluate(String conditionJson, RouteRequest request) {
        try {
            JsonNode condition = objectMapper.readTree(conditionJson);
            Map<String, Object> paymentData = buildPaymentData(request);
            return evaluateNode(condition, paymentData);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse condition JSON: {}", conditionJson, e);
            return false;
        }
    }

    private Map<String, Object> buildPaymentData(RouteRequest request) {
        return Map.of(
                "amount", request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO,
                "currency", request.getCurrency() != null ? request.getCurrency() : "",
                "channel", request.getChannel() != null ? request.getChannel() : "",
                "purpose_code", request.getPurposeCode() != null ? request.getPurposeCode() : ""
        );
    }

    private boolean evaluateNode(JsonNode node, Map<String, Object> paymentData) {
        if (node.has("and")) {
            JsonNode conditions = node.get("and");
            if (!conditions.isArray()) return false;
            for (JsonNode child : conditions) {
                if (!evaluateNode(child, paymentData)) {
                    return false;
                }
            }
            return true;
        }

        if (node.has("or")) {
            JsonNode conditions = node.get("or");
            if (!conditions.isArray()) return false;
            for (JsonNode child : conditions) {
                if (evaluateNode(child, paymentData)) {
                    return true;
                }
            }
            return false;
        }

        return evaluateSimpleCondition(node, paymentData);
    }

    private boolean evaluateSimpleCondition(JsonNode node, Map<String, Object> paymentData) {
        String field = node.has("field") ? node.get("field").asText() : null;
        String op = node.has("op") ? node.get("op").asText() : null;
        JsonNode valueNode = node.get("value");

        if (field == null || op == null || valueNode == null) {
            log.warn("Invalid condition node: missing field, op, or value");
            return false;
        }

        Object actualValue = paymentData.get(field);
        if (actualValue == null) {
            log.warn("Unknown field in condition: {}", field);
            return false;
        }

        return compareValues(actualValue, op, valueNode);
    }

    private boolean compareValues(Object actual, String op, JsonNode expected) {
        if (actual instanceof BigDecimal actualDecimal) {
            BigDecimal expectedDecimal = expected.isArray() ? null : new BigDecimal(expected.asText());

            return switch (op) {
                case "eq" -> actualDecimal.compareTo(expectedDecimal) == 0;
                case "neq" -> actualDecimal.compareTo(expectedDecimal) != 0;
                case "gt" -> actualDecimal.compareTo(expectedDecimal) > 0;
                case "gte" -> actualDecimal.compareTo(expectedDecimal) >= 0;
                case "lt" -> actualDecimal.compareTo(expectedDecimal) < 0;
                case "lte" -> actualDecimal.compareTo(expectedDecimal) <= 0;
                case "in" -> {
                    if (!expected.isArray()) yield false;
                    for (JsonNode item : expected) {
                        if (actualDecimal.compareTo(new BigDecimal(item.asText())) == 0) {
                            yield true;
                        }
                    }
                    yield false;
                }
                default -> {
                    log.warn("Unsupported operator for numeric comparison: {}", op);
                    yield false;
                }
            };
        }

        String actualStr = actual.toString();
        String expectedStr = expected.isArray() ? null : expected.asText();

        return switch (op) {
            case "eq" -> actualStr.equalsIgnoreCase(expectedStr);
            case "neq" -> !actualStr.equalsIgnoreCase(expectedStr);
            case "contains" -> actualStr.toLowerCase().contains(expectedStr != null ? expectedStr.toLowerCase() : "");
            case "in" -> {
                if (!expected.isArray()) yield false;
                Iterator<JsonNode> elements = expected.elements();
                while (elements.hasNext()) {
                    if (actualStr.equalsIgnoreCase(elements.next().asText())) {
                        yield true;
                    }
                }
                yield false;
            }
            case "gt", "gte", "lt", "lte" -> {
                int cmp = actualStr.compareToIgnoreCase(expectedStr != null ? expectedStr : "");
                yield switch (op) {
                    case "gt" -> cmp > 0;
                    case "gte" -> cmp >= 0;
                    case "lt" -> cmp < 0;
                    case "lte" -> cmp <= 0;
                    default -> false;
                };
            }
            default -> {
                log.warn("Unsupported operator for string comparison: {}", op);
                yield false;
            }
        };
    }
}
