/*
 * Entreri, an entity-component framework in Java
 *
 * Copyright (c) 2014, Michael Ludwig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright notice,
 *         this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *         this list of conditions and the following disclaimer in the
 *         documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.lhkbob.entreri.impl.apt;

import com.lhkbob.entreri.Component;
import com.lhkbob.entreri.IllegalComponentDefinitionException;
import com.lhkbob.entreri.Ownable;
import com.lhkbob.entreri.Owner;
import com.lhkbob.entreri.property.ImplementedBy;
import com.lhkbob.entreri.property.Property;
import com.lhkbob.entreri.property.Reference;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * ComponentSpecification
 * ======================
 *
 * ComponentSpecification provides an interface to access the information encoded in a Component
 * sub-interface in order to generate a proxy implementation. This class is responsible for validating all
 * aspects of a Component sub-interface, determining the Property implementations to use, and matching all
 * declared methods with method patterns. Use this in conjunction with {@link
 * com.lhkbob.entreri.impl.apt.ComponentGenerator} to output actual Java source code for the component proxy
 * implementations.
 *
 * @author Michael Ludwig
 */
public class ComponentSpecification {
    private final Context context;

    private final TypeMirror componentType;
    private final String packageName;
    private final List<PropertyDeclaration> properties;
    private final List<MethodDeclaration> methods;

    /**
     * Create a new ComponentSpecification for the given Component subinterface `type`, operating within
     * the APT processing environment given by `env`. This uses the default list of method patterns with
     * precedence:
     *
     * 1. {@link com.lhkbob.entreri.impl.apt.MultiSetterPattern}
     * 2. {@link com.lhkbob.entreri.impl.apt.SharedBeanGetterPattern}
     * 3. {@link com.lhkbob.entreri.impl.apt.BeanGetterPattern}
     * 4. {@link com.lhkbob.entreri.impl.apt.BeanSetterPattern}
     *
     * @param type The component type to analyze
     * @param env  The processing environment
     */
    public ComponentSpecification(TypeElement type, ProcessingEnvironment env) {
        // the shared bean getter must be before the collections method pattern to grab getters
        // annotated with @ReturnValue instead of matching the get-key pattern
        this(type, env, new SharedBeanGetterPattern(), new CollectionsMethodPattern(),
             new MultiSetterPattern(), new BeanGetterPattern(), new BeanSetterPattern());
    }

    /**
     * Create a new ComponentSpecification for the given Component sub-interface `type`, operating within
     * the APT processing environment given by `env`. This uses the provided list of method patterns
     * to match methods, where precedence is determined by the order provided.
     *
     * @param type     The component type to analyze
     * @param env      The processing environment
     * @param patterns The list of method patterns, with higher precedence first
     */
    public ComponentSpecification(TypeElement type, ProcessingEnvironment env, MethodPattern... patterns) {
        context = createContext(env, type.asType());
        TypeMirror componentType = context.fromClass(Component.class);
        TypeMirror objectType = context.fromClass(Object.class);
        TypeMirror ownerType = context.fromClass(Owner.class);
        TypeMirror ownableType = context.fromClass(Ownable.class);

        if (!context.getTypes().isAssignable(type.asType(), componentType)) {
            throw fail(type.asType(), "Class must extend Component");
        }
        if (!type.getKind().equals(ElementKind.INTERFACE)) {
            throw fail(type.asType(), "Component definition must be an interface");
        }

        List<ExecutableElement> methods = filterMethods(context, ElementFilter.methodsIn(context.getElements()
                                                                                                .getAllMembers(type)),
                                                        componentType, objectType, ownerType, ownableType);

        List<PropertyDeclaration> properties = new ArrayList<>();
        for (MethodPattern pattern : patterns) {
            Map<ExecutableElement, Collection<? extends PropertyDeclaration>> matches = pattern.match(context,
                                                                                                      methods);
            for (ExecutableElement m : matches.keySet()) {
                properties.addAll(matches.get(m));
                methods.remove(m);
            }
        }

        if (!methods.isEmpty()) {
            throw fail(type.asType(), "Not all methods can be implemented: " + methods);
        }

        properties = compactProperties(context, properties);
        validateAttributes(context, properties);
        assignPropertyImplementations(context, properties);
        Collections.sort(properties);

        // accumulate methods
        Set<MethodDeclaration> allMethods = new HashSet<>();
        for (PropertyDeclaration p : properties) {
            allMethods.addAll(p.getMethods());
        }
        List<MethodDeclaration> orderedMethods = new ArrayList<>(allMethods);
        Collections.sort(orderedMethods);
        validateMethods(context, orderedMethods);

        context.getLogger()
               .printMessage(Diagnostic.Kind.OTHER, getDebugSpecificationMessage(properties), type);

        this.properties = Collections.unmodifiableList(properties);
        this.methods = Collections.unmodifiableList(orderedMethods);
        this.componentType = context.getComponentType();
        packageName = context.getElements().getPackageOf(type).getQualifiedName().toString();
    }

    private static String getDebugSpecificationMessage(List<PropertyDeclaration> properties) {
        StringBuilder sb = new StringBuilder();
        sb.append("Detected properties").append(":\n");
        for (PropertyDeclaration p : properties) {
            sb.append("\t");
            String[] ms = p.toString().split("\\n");
            boolean firstLine = true;
            for (String s : ms) {
                if (firstLine) {
                    firstLine = false;
                } else {
                    sb.append("\n\t");
                }
                sb.append(s);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static IllegalComponentDefinitionException fail(TypeMirror type, String msg) {
        return new IllegalComponentDefinitionException(type.toString(), msg);
    }

    private static void validateAttributes(Context context, List<PropertyDeclaration> properties) {
        for (PropertyDeclaration p : properties) {
            Set<DeclaredType> seen = new HashSet<>();
            for (AnnotationMirror a : p.getAttributes()) {
                if (!seen.add(a.getAnnotationType())) {
                    throw fail(context.getComponentType(),
                               p.getName() + " has multiple distinct attribute values for type " +
                               a.getAnnotationType());
                }
            }
        }
    }

    private static void validateMethods(Context context, List<MethodDeclaration> methods) {
        for (MethodDeclaration m : methods) {
            if (!m.arePropertiesValid(context)) {
                throw new IllegalComponentDefinitionException("Method pattern does not support selected property implementations: " +
                                                              m.getMethod());
            }
        }
    }

    private static List<PropertyDeclaration> assignPropertyImplementations(Context context,
                                                                           List<PropertyDeclaration> properties) {
        for (PropertyDeclaration p : properties) {
            if (p.getType() == null) {
                throw new IllegalComponentDefinitionException(context.getComponentType().toString(),
                                                              "Property has under-determined type: " +
                                                              p.getName());
            }

            if (p.getPropertyImplementation() == null) {
                TypeMirror propType = null;

                for (AnnotationMirror a : p.getAttributes()) {
                    ImplementedBy impl = context.asAnnotation(a, ImplementedBy.class);
                    if (impl != null) {
                        try {
                            impl.value();
                        } catch (MirroredTypeException e) {
                            propType = e.getTypeMirror();
                        }
                        break;
                    }
                }

                if (propType == null) {
                    // look up from the file mapping after determining semantics
                    boolean useReferenceSemantics = false;
                    for (AnnotationMirror a : p.getAttributes()) {
                        if (context.isAnnotationType(a, Reference.class)) {
                            useReferenceSemantics = true;
                            break;
                        }
                    }
                    TypePropertyMapper mapper = (useReferenceSemantics ? context.getReferenceTypeMapper()
                                                                       : context.getValueTypeMapper());
                    propType = mapper.getPropertyFactory(p.getType());
                }

                p.setPropertyImplementation(propType);
            }
        }
        return properties;
    }

    private static List<PropertyDeclaration> compactProperties(Context context,
                                                               List<PropertyDeclaration> properties) {
        Map<String, PropertyDeclaration> compacted = new HashMap<>();
        for (PropertyDeclaration p : properties) {
            PropertyDeclaration compact = compacted.get(p.getName());
            if (compact == null) {
                // no compaction necessary yet
                compacted.put(p.getName(), p);
            } else {
                TypeMirror logicalType = compact.getType();
                TypeMirror propertyImpl = compact.getPropertyImplementation();

                if (p.getType() != null) {
                    // make sure that p's type is compatible with compact's type
                    if (compact.getType() != null) {
                        // the types must be equal
                        if (!context.getTypes().isSameType(compact.getType(), p.getType())) {
                            throw fail(context.getComponentType(),
                                       "Multiple methods create conflicting property type for " +
                                       p.getName());
                        }
                    }
                    logicalType = p.getType();

                    // ensure any specified property implementation is consistent
                    if (p.getPropertyImplementation() != null) {
                        if (compact.getPropertyImplementation() != null && !context.getTypes()
                                                                                   .isSameType(compact.getPropertyImplementation(),
                                                                                               p.getPropertyImplementation())) {
                            throw fail(context.getComponentType(),
                                       "Multiple methods use conflicting Property implementations for " +
                                       p.getName());
                        }
                        propertyImpl = p.getPropertyImplementation();
                    }
                }

                PropertyDeclaration merged = new PropertyDeclaration(context, p.getName(), logicalType,
                                                                     propertyImpl);
                merged.getAttributes().addAll(p.getAttributes());
                merged.getAttributes().addAll(compact.getAttributes());
                for (MethodDeclaration m : compact.getMethods()) {
                    m.replace(compact, merged);
                    merged.getMethods().add(m);
                }
                for (MethodDeclaration m : p.getMethods()) {
                    m.replace(p, merged);
                    merged.getMethods().add(m);
                }

                compacted.put(p.getName(), merged);
            }
        }

        return new ArrayList<>(compacted.values());
    }

    private static List<ExecutableElement> filterMethods(Context context, List<ExecutableElement> methods,
                                                         TypeMirror... unneeded) {
        List<ExecutableElement> filtered = new ArrayList<>();
        for (ExecutableElement m : methods) {
            TypeMirror declaredIn = TypeUtils.findEnclosingTypeElement(m).asType();
            boolean declaredInUnneeded = false;
            for (TypeMirror t : unneeded) {
                if (context.getTypes().isSameType(declaredIn, t)) {
                    declaredInUnneeded = true;
                    break;
                }
            }

            if (!declaredInUnneeded) {
                filtered.add(m);
            }
        }

        return filtered;
    }

    private static Context createContext(ProcessingEnvironment env, TypeMirror componentType) {
        return new Context(env, componentType, new TypePropertyMapper(env, Property.ValueSemantics.class),
                           new TypePropertyMapper(env, Property.ReferenceSemantics.class));
    }

    /**
     * @return The context used to produce this specification
     */
    public Context getContext() {
        return context;
    }

    /**
     * Get the qualified name of the component type, including the package name reported by {@link
     * #getPackage()}. Thus, the returned string should be valid to insert into source code regardless of if
     * there's another property or type that may have the same class name.
     *
     * @return The component type
     */
    public TypeMirror getType() {
        return componentType;
    }

    /**
     * @return The package the component type resides in
     */
    public String getPackage() {
        return packageName;
    }

    /**
     * Get all properties that must be implemented for this component type. This will include all properties
     * defined in a parent component type if the type does not directly extend Component.
     *
     * The returned list will be immutable and sorted by logical property name.
     *
     * @return The list of all properties for the component
     */
    public List<PropertyDeclaration> getProperties() {
        return properties;
    }

    /**
     * Get all methods that must be implemented. This is the union of all methods from the property
     * declarations of this specification, with duplicates removed, and ordered by the method name.
     *
     * @return The list of methods the component type must implement to compile correctly
     */
    public List<? extends MethodDeclaration> getMethods() {
        return methods;
    }
}
