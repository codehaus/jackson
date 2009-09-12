package org.codehaus.jackson.map.type;

import java.util.*;
import java.lang.reflect.*;

import org.codehaus.jackson.type.JavaType;
import org.codehaus.jackson.type.TypeReference;

/**
 * Class that knows how construct {@link JavaType} instances,
 * given various inputs.
 */
public class TypeFactory
{
    public final static TypeFactory instance = new TypeFactory();

    /*
    //////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////
     */

    private TypeFactory() { }

    /*
    //////////////////////////////////////////////////
    // Public factory methods
    //////////////////////////////////////////////////
     */

    /**
     * Factory method that can be used if only type information
     * available is of type {@link Class}. This means that there
     * will not be generic type information due to type erasure,
     * but at least it will be possible to recognize array
     * types and non-typed container types.
     * And for other types (primitives/wrappers, beans), this
     * is all that is needed.
     */
    public static JavaType fromClass(Class<?> clz)
    {
        return instance._fromClass(clz, null);
    }

    /**
     * Factory method that can be used if the full generic type has
     * been passed using {@link TypeReference}. This only needs to be
     * done if the root type to bind to is generic; but if so,
     * it must be done to get proper typing.
     */
    public static JavaType fromTypeReference(TypeReference<?> ref)
    {
        return fromType(ref.getType());
    }

    /**
     * Factory method that can be used if type information is passed
     * as Java typing returned from <code>getGenericXxx</code> methods
     * (usually for a return or argument type).
     */
    public static JavaType fromType(Type type)
    {
        return instance._fromType(type, null);
    }

    /**
     * @param context Type context that can be used for binding
     *   named formal type parameters, if any (if null, no context
     *   is used)
     */
    public static JavaType fromType(Type type, JavaType context)
    {
        return instance._fromType(type, context);
    }

    /*
    ///////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////
     */

    /**
     * @param genericParams Mapping of formal parameter declarations (for generic
     *   types) into actual types
     */
    protected JavaType _fromClass(Class<?> clz, Map<String,JavaType> genericParams)
    {
        // First: do we have an array type?
        if (clz.isArray()) {
            return ArrayType.construct(fromClass(clz.getComponentType()));
        }
        /* Maps and Collections aren't quite as hot; problem is, due
         * to type erasure we can't know typing and can only assume
         * base Object... at any rate, whether that's a problem is up
         * to caller to decide: we'll just flag this (resulting type
         * instance will return 'false' from its 'isFullyTyped' method)
         */
        if (Map.class.isAssignableFrom(clz)) {
            JavaType unknownType = fromClass(Object.class);
            return MapType.untyped(clz, unknownType, unknownType);
        }
        if (Collection.class.isAssignableFrom(clz)) {
            JavaType unknownType = fromClass(Object.class);
            return CollectionType.untyped(clz, unknownType);
        }
        /* Otherwise, consider it a Bean; and due to type
         * erasure it must be simple (no generics available)
         */
        return new SimpleType(clz, genericParams);
    }

    /**
     * Factory method that can be used if type information is passed
     * as Java typing returned from <code>getGenericXxx</code> methods
     * (usually for a return or argument type).
     */
    public JavaType _fromType(Type type, JavaType context)
    {
        // may still be a simple type...
        if (type instanceof Class) {
            return _fromClass((Class<?>) type, null);
        }
        // But if not, need to start resolving.
        if (type instanceof ParameterizedType) {
            return _fromParamType((ParameterizedType) type, context);
        }
        if (type instanceof GenericArrayType) {
            return _fromArrayType((GenericArrayType) type, context);
        }
        if (type instanceof TypeVariable) {
            return _fromVariable((TypeVariable<?>) type, context);
        }
        if (type instanceof WildcardType) {
            return _fromWildcard((WildcardType) type, context);
        }
        // sanity check
        throw new IllegalArgumentException("Unrecognized Type: "+type.toString());
    }

    /**
     * This method deals with parameterized types, that is,
     * first class generic classes.
     * We actually only care about generics
     * iff they are of type Map or Collection -- otherwise
     * we'll just use the raw type. This because we have no
     * idea how to handle genericized classes in, well, generic
     * fashion. For other types we will then just fall back
     * to using "raw" class information.
     */
    protected JavaType _fromParamType(ParameterizedType type, JavaType context)
    {
        /* First: what is the actual base type? One odd thing
         * is that 'getRawType' returns Type, not Class<?> as
         * one might expect. But let's assume it is always of
         * type Class: if not, need to add more code to resolve
         * it to Class.
         */
        Class<?> rawType = (Class<?>) type.getRawType();

        // Ok: Map or Collection?
        if (Map.class.isAssignableFrom(rawType)) {
            Type[] args = type.getActualTypeArguments();
            return MapType.typed(rawType, fromType(args[0]), fromType(args[1]));
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            return CollectionType.typed(rawType, fromType(type.getActualTypeArguments()[0]));
        }

        // Maybe a generics version?
        Type[] args = type.getActualTypeArguments();
        Map<String,JavaType> types = null;

        if (args != null && args.length > 0) {
            /* If so, need mapping from name to type, to allow resolving
             * of generic types
             */
            TypeVariable<?>[] vars;

            vars = rawType.getTypeParameters();
            // Sanity check:
            if (vars.length != args.length) {
                throw new IllegalArgumentException
                    ("Strange parametrized type (raw: "+rawType+"): number of type arguments != number of type parameters ("+args.length+" vs "+vars.length+")");
            }
            types = new HashMap<String,JavaType>();
            for (int i = 0, len = args.length; i < len; ++i) {
                types.put(vars[i].getName(), _fromType(args[i], context));
            }
        }
        /* Neither: well, let's just consider it a bean or such;
         * may or not may not be a problem.
         */
        return _fromClass(rawType, types);
    }

    protected JavaType _fromArrayType(GenericArrayType type, JavaType context)
    {
        JavaType compType = _fromType(type.getGenericComponentType(), context);
        return ArrayType.construct(compType);
    }

    protected JavaType _fromVariable(TypeVariable<?> type, JavaType context)
    {
        // Ok: here's where context might come in handy!
        String name = type.getName();
        JavaType actualType = context.findVariableType(name);
        if (actualType != null) {
            return actualType;
        }

        /* 16-Jun-2009, tatu: Instead of trying to figure out graceful
         *   fallback, let's just throw an Exception: chances are caller
         *   wouldn't find it very intuitive to get "untyped" binding.
         *   Plus variables just should not remain unexpanded.
         */
        throw new IllegalArgumentException
            ("Unresolved TypeVariable <"+name+"> (from "+type.getGenericDeclaration()+")");

        // Old code for reference:

        /*
        Type[] bounds = type.getBounds();

        // With type variables we must use bound information.
        // Theoretically this gets tricky, as there may be multiple
        // bounds ("... extends A & B"); and optimally we might
        // want to choose the best match. Also, bounds are optional;
        // but here we are lucky in that implicit "Object" is
        // added as bounds if so.
        // Either way let's just use the first bound, for now, and
        // worry about better match later on if there is need.
        return _fromType(bounds[0]);
        */
    }

    protected JavaType _fromWildcard(WildcardType type, JavaType context)
    {
        /* Similar to challenges with TypeVariable, we may have
         * multiple upper bounds. But it is also possible that if
         * upper bound defaults to Object, we might want to consider
         * lower bounds instead.
         *
         * For now, we won't try anything more advanced; above is
         * just for future reference.
         */
        return _fromType(type.getUpperBounds()[0], context);
    }
}
