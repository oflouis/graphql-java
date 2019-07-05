package graphql.execution.nextgen.depgraph;

import graphql.Assert;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitorStub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static graphql.util.FpKit.map;

public class DependencyGraph {


    public DependencyGraph createDependencyGraph(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {

        FieldCollectorWTC fieldCollector = new FieldCollectorWTC();
        FieldCollectorParameters parameters = FieldCollectorParameters
                .newParameters()
                .schema(graphQLSchema)
                .build();

        Function<MergedFieldWTC, List<MergedFieldWTC>> getChildren = mergedFieldWTC -> {
            List<MergedFieldWTC> childs = fieldCollector.collectFields(parameters, mergedFieldWTC);
            return childs;
        };

        Traverser<MergedFieldWTC> traverser = Traverser.depthFirst(getChildren);
        FieldVertex rootVertex = new FieldVertex(null, null, null, null, null);
        traverser.rootVar(FieldVertex.class, rootVertex);
        List<MergedFieldWTC> roots = fieldCollector.collectFromOperation(parameters, operationDefinition, graphQLSchema.getQueryType());
        traverser.traverse(roots, new TraverserVisitorStub<MergedFieldWTC>() {
            @Override
            public TraversalControl enter(TraverserContext<MergedFieldWTC> context) {
                MergedFieldWTC mergedFieldWTC = context.thisNode();
                System.out.println(mergedFieldWTC.getName() + "" + map(mergedFieldWTC.getTypeConditions(), GraphQLType::getName));
//                List<MergedFieldWTC> parentNodes = context.getParentNodes();
//                Collections.reverse(parentNodes);

//                List<String> keys = map(parentNodes, parentNode -> {
//                    return parentNode.getResultKey() + getPossibleObjectTypesString(parentNode, graphQLSchema);
//                });
//                String queryPath = String.join("/", keys);
//                queryPath = "/" + queryPath + "/" + mergedFieldWTC.getResultKey() + getPossibleObjectTypesString(mergedFieldWTC, graphQLSchema);
////                System.out.println("-----------");
////                System.out.println("visited key:" + context.thisNode().getResultKey());
//                System.out.println("query path: " + queryPath + " field type: " + context.thisNode().getFieldDefinition().getType() + " container: " + context.thisNode().getFieldsContainer().getName());
//                System.out.println("visited: " + context.thisNode());


                FieldVertex parentVertex = context.getVarFromParents(FieldVertex.class);
                FieldVertex fieldVertex = createFieldVertex(mergedFieldWTC, graphQLSchema);
                context.setVar(FieldVertex.class, fieldVertex);
                parentVertex.addChild(fieldVertex);

                return TraversalControl.CONTINUE;
            }

        });

        StringBuilder dotFile = new StringBuilder();
        dotFile.append("digraph G{\n");
        Traverser<FieldVertex> traverserVertex = Traverser.depthFirst(FieldVertex::getChildren);

        List<FieldVertex> allVertices = new ArrayList<>();
        traverserVertex.traverse(rootVertex, new TraverserVisitorStub<FieldVertex>() {
            @Override
            public TraversalControl enter(TraverserContext<FieldVertex> context) {
                FieldVertex fieldVertex = context.thisNode();
                FieldVertex parentVertex = context.getParentNode();
                if (parentVertex != null) {
                    String vertexId = fieldVertex.getClass().getSimpleName() + Integer.toHexString(fieldVertex.hashCode());
                    String parentVertexId = parentVertex.getClass().getSimpleName() + Integer.toHexString(parentVertex.hashCode());
                    dotFile.append(vertexId).append(" -> ").append(parentVertexId).append(";\n");
                }
                allVertices.add(context.thisNode());
                return TraversalControl.CONTINUE;
            }
        });

        for (FieldVertex fieldVertex : allVertices) {
            dotFile.append(fieldVertex.getClass().getSimpleName()).append(Integer.toHexString(fieldVertex.hashCode())).append("[label=\"")
                    .append(fieldVertex.toString()).append("\"];\n");
        }

        dotFile.append("}");
        System.out.println(dotFile);
        return null;
    }

    private FieldVertex createFieldVertex(MergedFieldWTC mergedFieldWTC, GraphQLSchema graphQLSchemas) {
        return new FieldVertex(mergedFieldWTC.getFields(),
                mergedFieldWTC.getFieldDefinition(),
                mergedFieldWTC.getFieldsContainer(),
                mergedFieldWTC.getParentType(),
                mergedFieldWTC.getTypeConditions()
        );

    }

    private List<String> getPossibleObjectTypesString(MergedFieldWTC mergedFieldWTC, GraphQLSchema graphQLSchema) {
        return map(getPossibleObjectTypes(mergedFieldWTC, graphQLSchema), GraphQLObjectType::getName);
    }

    private List<GraphQLObjectType> getPossibleObjectTypes(MergedFieldWTC mergedFieldWTC, GraphQLSchema graphQLSchema) {
        List<GraphQLObjectType> possibleObjects = getImplicitObjects(graphQLSchema, mergedFieldWTC);

        if (mergedFieldWTC.getTypeConditions().isEmpty()) {
        } else {
            for (GraphQLCompositeType typeCondition : mergedFieldWTC.getTypeConditions()) {
                if (typeCondition instanceof GraphQLObjectType) {
                    possibleObjects.retainAll(Collections.singleton(typeCondition));
                } else if (typeCondition instanceof GraphQLInterfaceType) {
                    possibleObjects.retainAll(graphQLSchema.getImplementations((GraphQLInterfaceType) typeCondition));
                } else if (typeCondition instanceof GraphQLUnionType) {
                    possibleObjects.retainAll(((GraphQLUnionType) typeCondition).getTypes());
                }
            }
        }
        return possibleObjects;
    }

    private List<GraphQLObjectType> getImplicitObjects(GraphQLSchema graphQLSchema, MergedFieldWTC mergedFieldWTC) {
        List<GraphQLObjectType> result = new ArrayList<>();
        GraphQLFieldsContainer fieldsContainer = mergedFieldWTC.getFieldsContainer();
        if (fieldsContainer instanceof GraphQLInterfaceType) {
            result.addAll(graphQLSchema.getImplementations((GraphQLInterfaceType) fieldsContainer));
        } else if (fieldsContainer instanceof GraphQLObjectType) {
            result.add((GraphQLObjectType) fieldsContainer);
        } else {
            return Assert.assertShouldNeverHappen();
        }
        return result;
    }


    public static void main(String[] args) {

    }
}