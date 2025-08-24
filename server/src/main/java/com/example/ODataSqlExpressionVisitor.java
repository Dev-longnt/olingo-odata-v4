package com.example;

import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor;
import org.apache.olingo.server.api.uri.queryoption.expression.Literal;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.apache.olingo.server.api.uri.queryoption.expression.MethodKind;
import org.apache.olingo.server.api.uri.queryoption.expression.UnaryOperatorKind;

public class ODataSqlExpressionVisitor implements ExpressionVisitor<String> {

    private final String mainTableAlias;
    private final List<Object> parameters; // To store PreparedStatement parameters

    public ODataSqlExpressionVisitor(String mainTableAlias, List<Object> parameters) {
        this.mainTableAlias = mainTableAlias;
        this.parameters = parameters;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    @Override
    public String visitBinaryOperator(BinaryOperatorKind operator, String left, String right) throws ODataApplicationException {
        String sqlOperator;
        switch (operator) {
            case EQ:    sqlOperator = "="; break;
            case NE:    sqlOperator = "<>"; break;
            case GT:    sqlOperator = ">"; break;
            case GE:    sqlOperator = ">="; break;
            case LT:    sqlOperator = "<"; break;
            case LE:    sqlOperator = "<="; break;
            case AND:   sqlOperator = "AND"; break;
            case OR:    sqlOperator = "OR"; break;
            // Add more operators as needed
            default:
                throw new ODataApplicationException("Unsupported binary operator: " + operator.name(),
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
        return "(" + left + " " + sqlOperator + " " + right + ")";
    }

    @Override
    public String visitUnaryOperator(UnaryOperatorKind operator, String operand) throws ODataApplicationException {
        String sqlOperator;
        switch (operator) {
            case NOT: sqlOperator = "NOT"; break;
            // Add more operators as needed
            default:
                throw new ODataApplicationException("Unsupported unary operator: " + operator.name(),
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
        return sqlOperator + " " + operand;
    }

    @Override
    public String visitMethodCall(MethodKind method, List<String> parameters) throws ODataApplicationException {
        // Implement method calls like contains, startswith, endswith, etc.
        // This is complex and will require careful mapping to SQL functions.
        throw new ODataApplicationException("Unsupported method call: " + method.name(),
            HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitLiteral(Literal literal) throws ODataApplicationException {
        // Add parameter to the list for PreparedStatement
        parameters.add(literal.getText()); // This might need type conversion
        return "?";
    }

    @Override
    public String visitMember(Member member) throws ODataApplicationException {
        // This represents a property access (e.g., "Price")
        // Need to get the property name and prepend the table alias
        // member.getResourcePath().getUriResourceParts() will give the path segments
        // For simple properties, it's just the property name
        if (member.getResourcePath().getUriResourceParts().size() == 1) {
            return mainTableAlias + "." + member.getResourcePath().getUriResourceParts().get(0).getSegmentValue();
        }
        throw new ODataApplicationException("Unsupported member expression",
            HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
    }

    // Other visit methods for different expression types (e.g., LambdaExpression, TypeLiteral, etc.)
    // For now, throw unsupported exceptions for unhandled types.

    @Override
    public String visitTypeLiteral(EdmType type) throws ODataApplicationException {
        // TypeLiteral is rarely used in SQL translation; return its type name
        return "'" + type.getFullQualifiedName().getFullQualifiedNameAsString() + "'";
    }

    @Override
    public String visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression) throws ODataApplicationException {
        // Lambda expressions (any/all) are not directly supported in SQL here
        throw new ODataApplicationException("Lambda expressions are not supported in SQL translation.",
            HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitAlias(String aliasName) throws ODataApplicationException {
        // Aliases are not supported in this visitor
        throw new ODataApplicationException("Aliases are not supported in SQL translation.",
            HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitEnum(EdmEnumType type, List<String> enumValues) throws ODataApplicationException {
        // Convert enum values to their string representation for SQL
        if (enumValues == null || enumValues.isEmpty()) {
            throw new ODataApplicationException("Enum values are missing.",
                HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enumValues.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(enumValues.get(i)).append("'");
        }
        return sb.toString();
    }

    public String visitCustomExpression(Expression expression) throws ODataApplicationException {
        // Custom expressions are not supported
        throw new ODataApplicationException("Custom expressions are not supported in SQL translation.",
            HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitLambdaReference(String variableName) throws ODataApplicationException {
        // Lambda references are not supported
        throw new ODataApplicationException("Lambda references are not supported in SQL translation.",
            HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public String visitBinaryOperator(BinaryOperatorKind operator, String left, List<String> right) throws ODataApplicationException {
        // Binary operators with a list (e.g., IN) can be mapped to SQL IN clause
        if (operator == BinaryOperatorKind.IN) {
            StringBuilder sb = new StringBuilder();
            sb.append(left).append(" IN (");
            for (int i = 0; i < right.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(right.get(i));
            }
            sb.append(")");
            return sb.toString();
        }
        throw new ODataApplicationException("Unsupported binary operator with list: " + operator.name(),
            HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
    }
}