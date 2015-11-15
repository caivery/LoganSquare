package com.bluelinelabs.logansquare.processor;

import com.bluelinelabs.logansquare.Constants;
import com.bluelinelabs.logansquare.JsonMapper;
import com.bluelinelabs.logansquare.ParameterizedType;
import com.bluelinelabs.logansquare.internal.JsonMapperLoader;
import com.bluelinelabs.logansquare.internal.objectmappers.BooleanMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.DoubleMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.FloatMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.IntegerMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.ListMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.LongMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.MapMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.ObjectMapper;
import com.bluelinelabs.logansquare.internal.objectmappers.StringMapper;
import com.bluelinelabs.logansquare.util.SimpleArrayMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;

public class JsonMapperLoaderInjector {

    private final Collection<JsonObjectHolder> mJsonObjectHolders;

    public JsonMapperLoaderInjector(Collection<JsonObjectHolder> jsonObjectHolders) {
        mJsonObjectHolders = jsonObjectHolders;
    }

    public String getJavaClassFile() {
        try {
            return JavaFile.builder(Constants.LOADER_PACKAGE_NAME, getTypeSpec()).build().toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private TypeSpec getTypeSpec() {
        TypeSpec.Builder builder = TypeSpec.classBuilder(Constants.LOADER_CLASS_NAME).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        builder.addSuperinterface(ClassName.get(JsonMapperLoader.class));

        addAllBuiltInMappers(builder);
        builder.addMethod(getPutAllJsonMappersMethod(builder));

        addParameterizedMapperGetters(builder);

        return builder.build();
    }

    private void addAllBuiltInMappers(TypeSpec.Builder typeSpecBuilder) {
        addBuiltInMapper(typeSpecBuilder, StringMapper.class);
        addBuiltInMapper(typeSpecBuilder, IntegerMapper.class);
        addBuiltInMapper(typeSpecBuilder, LongMapper.class);
        addBuiltInMapper(typeSpecBuilder, FloatMapper.class);
        addBuiltInMapper(typeSpecBuilder, DoubleMapper.class);
        addBuiltInMapper(typeSpecBuilder, BooleanMapper.class);
        addBuiltInMapper(typeSpecBuilder, ObjectMapper.class);
        addBuiltInMapper(typeSpecBuilder, ListMapper.class);
        addBuiltInMapper(typeSpecBuilder, MapMapper.class);
    }

    private void addBuiltInMapper(TypeSpec.Builder typeSpecBuilder, Class mapperClass) {
        typeSpecBuilder.addField(FieldSpec.builder(mapperClass, getMapperVariableName(mapperClass))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", mapperClass)
                .build()
        );
    }

    private MethodSpec getPutAllJsonMappersMethod(TypeSpec.Builder typeSpecBuilder) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("putAllJsonMappers")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterizedTypeName.get(ClassName.get(SimpleArrayMap.class), ClassName.get(Class.class), ClassName.get(JsonMapper.class)), "map")
                .addStatement("map.put($T.class, $L)", String.class, getMapperVariableName(StringMapper.class))
                .addStatement("map.put($T.class, $L)", Integer.class, getMapperVariableName(IntegerMapper.class))
                .addStatement("map.put($T.class, $L)", Long.class, getMapperVariableName(LongMapper.class))
                .addStatement("map.put($T.class, $L)", Float.class, getMapperVariableName(FloatMapper.class))
                .addStatement("map.put($T.class, $L)", Double.class, getMapperVariableName(DoubleMapper.class))
                .addStatement("map.put($T.class, $L)", Boolean.class, getMapperVariableName(BooleanMapper.class))
                .addStatement("map.put($T.class, $L)", Object.class, getMapperVariableName(ObjectMapper.class))
                .addStatement("map.put($T.class, $L)", List.class, getMapperVariableName(ListMapper.class))
                .addStatement("map.put($T.class, $L)", ArrayList.class, getMapperVariableName(ListMapper.class))
                .addStatement("map.put($T.class, $L)", Map.class, getMapperVariableName(MapMapper.class))
                .addStatement("map.put($T.class, $L)", HashMap.class, getMapperVariableName(MapMapper.class));

        List<String> createdMappers = new ArrayList<>();
        for (JsonObjectHolder jsonObjectHolder : mJsonObjectHolders) {
            if (jsonObjectHolder.typeParameters.size() == 0) {
                TypeName mapperTypeName = ClassName.get(jsonObjectHolder.packageName, jsonObjectHolder.injectedClassName);
                String variableName = getMapperVariableName(jsonObjectHolder.packageName + "." + jsonObjectHolder.injectedClassName);

                typeSpecBuilder.addField(FieldSpec.builder(mapperTypeName, variableName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .build()
                );

                createdMappers.add(variableName);
                builder.addStatement("$L = new $T()", variableName, mapperTypeName);

                if (!jsonObjectHolder.isAbstractClass) {
                    builder.addStatement("map.put($T.class, $L)", jsonObjectHolder.objectTypeName, variableName);
                }
            }
        }

        for (String mapper : createdMappers) {
            builder.addStatement("$L.ensureParent()", mapper);
        }

        return builder.build();
    }

    private void addParameterizedMapperGetters(TypeSpec.Builder builder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("mapperFor")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(TypeVariableName.get("T"))
                .returns(ParameterizedTypeName.get(ClassName.get(JsonMapper.class), TypeVariableName.get("T")))
                .addParameter(ParameterizedTypeName.get(ClassName.get(ParameterizedType.class), TypeVariableName.get("T")), "type")
                .addParameter(ParameterizedTypeName.get(ClassName.get(SimpleArrayMap.class), ClassName.get(ParameterizedType.class), ClassName.get(JsonMapper.class)), "partialMappers");

        boolean conditionalStarted = false;
        for (JsonObjectHolder jsonObjectHolder : mJsonObjectHolders) {
            if (jsonObjectHolder.typeParameters.size() > 0) {
                String conditional = String.format("if (type.rawType == %s.class)", jsonObjectHolder.objectTypeName.toString().replaceAll("<(.*?)>", ""));
                if (conditionalStarted) {
                    methodBuilder.nextControlFlow("else " + conditional);
                } else {
                    conditionalStarted = true;
                    methodBuilder.beginControlFlow(conditional);
                }

                methodBuilder.beginControlFlow("if (type.typeParameters.size() == $L)", jsonObjectHolder.typeParameters.size());

                StringBuilder constructorArgs = new StringBuilder();
                for (int i = 0; i < jsonObjectHolder.typeParameters.size(); i++) {
                    constructorArgs.append(", type.typeParameters.get(").append(i).append(")");
                }
                methodBuilder.addStatement("return new $T(type" + constructorArgs.toString() + ", partialMappers)", ClassName.get(jsonObjectHolder.packageName, jsonObjectHolder.injectedClassName));

                methodBuilder.nextControlFlow("else");
                methodBuilder.addStatement(
                        "throw new $T(\"Invalid number of parameter types. Type $T expects $L parameter types, received \" + type.typeParameters.size())",
                        RuntimeException.class, jsonObjectHolder.objectTypeName, jsonObjectHolder.typeParameters.size()
                );
                methodBuilder.endControlFlow();
            }
        }

        if (conditionalStarted) {
            methodBuilder.endControlFlow();
        }

        methodBuilder.addStatement("return null");

        builder.addMethod(methodBuilder.build());
    }

    public static String getMapperVariableName(Class cls) {
        return getMapperVariableName(cls.getCanonicalName());
    }

    public static String getMapperVariableName(String fullyQualifiedClassName) {
        return fullyQualifiedClassName.replaceAll("\\.", "_").replaceAll("\\$", "_").toUpperCase();
    }
}
