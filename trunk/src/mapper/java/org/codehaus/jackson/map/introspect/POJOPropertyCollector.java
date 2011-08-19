package org.codehaus.jackson.map.introspect;

/**
 * Helper class used for aggregating information about a single
 * potential POJO property.
 * 
 * @since 1.9
 */
public class POJOPropertyCollector
{
    /**
     * Name of logical property
     */
    protected final String _name;

    protected Node<AnnotatedField> _fields;
    
    protected Node<AnnotatedParameter> _ctorParameters;
    
    protected Node<AnnotatedMethod> _getters;

    protected Node<AnnotatedMethod> _setters;

    public POJOPropertyCollector(String name) {
        _name = name;
    }

    public POJOPropertyCollector(POJOPropertyCollector src, String newName) {
        _name = newName;
        _fields = src._fields;
        _ctorParameters = src._ctorParameters;
        _getters = src._getters;
        _setters = src._setters;
    }

    /**
     * Method for constructing a renamed instance
     */
    public POJOPropertyCollector withName(String newName) {
        return new POJOPropertyCollector(this, newName);
    }
    
    /*
    /**********************************************************
    /* Data aggregation
    /**********************************************************
     */

    public void addField(AnnotatedField a, String ename, boolean ignored) {
        _fields = new Node<AnnotatedField>(a, _fields, ename, ignored);
    }

    public void addCtor(AnnotatedParameter a, String ename,boolean ignored) {
        _ctorParameters = new Node<AnnotatedParameter>(a, _ctorParameters, ename, ignored);
    }

    public void addGetter(AnnotatedMethod a, String ename,boolean ignored) {
        _getters = new Node<AnnotatedMethod>(a, _getters, ename, ignored);
    }

    public void addSetter(AnnotatedMethod a, String ename,  boolean ignored) {
        _setters = new Node<AnnotatedMethod>(a, _setters, ename, ignored);
    }

    /**
     * Method for adding all property members from specified collector into
     * this collector.
     */
    public void addAll(POJOPropertyCollector src) {
        _fields = _addAll(_fields, src._fields);
        _ctorParameters = _addAll(_ctorParameters, src._ctorParameters);
        _getters= _addAll(_getters, src._getters);
        _setters = _addAll(_setters, src._setters);
    }

    private <T> Node<T> _addAll(Node<T> chainToAugment, Node<T> newEntries)
    {
        if (chainToAugment == null) {
            return newEntries;
        }
        while (newEntries != null) {
            Node<T> next = newEntries.next;
            chainToAugment = newEntries.relinked(chainToAugment);
            newEntries = next;
        }
        return chainToAugment;
    }
    
    /*
    /**********************************************************
    /* Modifications
    /**********************************************************
     */

    /**
     * Method called to remove all entries that are marked as
     * ignored.
     */
    public void removeIgnored()
    {
        _fields = _removeIgnored(_fields);
        _getters = _removeIgnored(_getters);
        _setters = _removeIgnored(_setters);
    }

    private <T> Node<T> _removeIgnored(Node<T> node)
    {
        // first, see if there is anything to remove
        if (!_anyIgnorals(node)) {
            return node;
        }
        // find first non-ignorable
        while (node != null && node.isMarkedIgnored) {
            node = node.next;
        }
        // none?
        if (node == null) {
            return null;
        }
        /* ok; recreate... note that this reverses order; should
         * not matter greatly as order is not meaningful
         */
        final Node<T> head = node.relinked(null);
        Node<T> tail = head;
        while (true) {
            node = node.next;
            if (node == null) {
                break;
            }
            if (node.isMarkedIgnored) {
                continue;
            }
            tail = node.relinked(tail);
        }
        return head;
    }
    
    /*
    /**********************************************************
    /* Simple accessors
    /**********************************************************
     */

    public String getName() { return _name; }

    public boolean hasGetter() { return _getters != null; }
    public boolean hasSetter() { return _setters != null; }
    public boolean hasField() { return _fields != null; }

    public AnnotatedMethod getGetter()
    {
        if (_getters == null) {
            return null;
        }
        // If multiple, verify that they do not conflict...
        AnnotatedMethod getter = _getters.value;
        Node<AnnotatedMethod> next = _getters.next;
        for (; next != null; next = next.next) {
            /* [JACKSON-255] Allow masking, i.e. report exception only if
             *   declarations in same class, or there's no inheritance relationship
             *   (sibling interfaces etc)
             */
            AnnotatedMethod nextGetter = next.value;
            Class<?> getterClass = getter.getDeclaringClass();
            Class<?> nextClass = nextGetter.getDeclaringClass();
            if (getterClass != nextClass) {
                if (getterClass.isAssignableFrom(nextClass)) { // next is more specific
                    getter = nextGetter;
                    continue;
                }
                if (nextClass.isAssignableFrom(getterClass)) { // getter more specific
                    continue;
                }
            }
            throw new IllegalArgumentException("Conflicting getter definitions for property \""+getName()+"\": "
                    +getter.getFullName()+" vs "+nextGetter.getFullName());
        }
        return getter;
    }

    public AnnotatedMethod getSetter()
    {
        if (_setters == null) {
            return null;
        }
        // If multiple, verify that they do not conflict...
        AnnotatedMethod setter = _setters.value;
        Node<AnnotatedMethod> next = _setters.next;
        for (; next != null; next = next.next) {
            /* [JACKSON-255] Allow masking, i.e. report exception only if
             *   declarations in same class, or there's no inheritance relationship
             *   (sibling interfaces etc)
             */
            AnnotatedMethod nextSetter = next.value;
            Class<?> setterClass = setter.getDeclaringClass();
            Class<?> nextClass = nextSetter.getDeclaringClass();
            if (setterClass != nextClass) {
                if (setterClass.isAssignableFrom(nextClass)) { // next is more specific
                    setter = nextSetter;
                    continue;
                }
                if (nextClass.isAssignableFrom(setterClass)) { // getter more specific
                    continue;
                }
            }
            throw new IllegalArgumentException("Conflicting setter definitions for property \""+getName()+"\": "
                    +setter.getFullName()+" vs "+nextSetter.getFullName());
        }
        return setter;
    }

    public AnnotatedField getField()
    {
        if (_fields == null) {
            return null;
        }
        // If multiple, verify that they do not conflict...
        AnnotatedField field = _fields.value;
        Node<AnnotatedField> next = _fields.next;
        for (; next != null; next = next.next) {
            AnnotatedField nextField = next.value;
            Class<?> fieldClass = field.getDeclaringClass();
            Class<?> nextClass = nextField.getDeclaringClass();
            if (fieldClass != nextClass) {
                if (fieldClass.isAssignableFrom(nextClass)) { // next is more specific
                    field = nextField;
                    continue;
                }
                if (nextClass.isAssignableFrom(fieldClass)) { // getter more specific
                    continue;
                }
            }
            throw new IllegalArgumentException("Multiple fields defined for property \""+getName()+"\": "
                    +field.getFullName()+" vs "+nextField.getFullName());
        }
        return field;
    }
    
    /*
    /**********************************************************
    /* Accessors for aggregate information
    /**********************************************************
     */

    public boolean anyExplicitNames() {
        return _anyExplicitNames(_fields)
                || _anyExplicitNames(_getters)
                || _anyExplicitNames(_setters)
                || _anyExplicitNames(_ctorParameters)
                ;
    }

    private <T> boolean _anyExplicitNames(Node<T> n)
    {
        for (; n != null; n = n.next) {
            if (n.explicitName != null && n.explicitName.length() > 0) {
                return true;
            }
        }
        return false;
    }
    
    public boolean anyIgnorals() {
        return _anyIgnorals(_fields)
                || _anyIgnorals(_getters)
                || _anyIgnorals(_setters)
                ;
    }

    private <T> boolean _anyIgnorals(Node<T> n)
    {
        for (; n != null; n = n.next) {
            if (n.isMarkedIgnored) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method called to check whether property represented by this collector
     * should be renamed from the implicit name; and also verify that there
     * are no conflicting rename definitions.
     */
    public String findNewName()
    {
        Node<? extends AnnotatedMember> renamed = null;
        renamed = findRenamed(_fields, renamed);
        renamed = findRenamed(_getters, renamed);
        renamed = findRenamed(_setters, renamed);
        renamed = findRenamed(_ctorParameters, renamed);
        return (renamed == null) ? null : renamed.explicitName;
    }

    private Node<? extends AnnotatedMember> findRenamed(Node<? extends AnnotatedMember> node,
            Node<? extends AnnotatedMember> renamed)
    {
        for (; node != null; node = node.next) {
            String explName = node.explicitName;
            if (explName == null) {
                continue;
            }
            // different from default name?
            if (explName.equals(_name)) { // nope, skip
                continue;
            }
            if (renamed == null) {
                renamed = node;
            } else {
                // different from an earlier renaming? problem
                if (!explName.equals(renamed.explicitName)) {
                    throw new IllegalStateException("Conflicting property name definitions: '"
                            +renamed.explicitName+"' (for "+renamed.value+") vs '"
                            +node.explicitName+"' (for "+node.value+")");
                }
            }
        }
        return renamed;
    }

    // For trouble-shooting
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[Property '").append(_name)
          .append("'; ctors: ").append(_ctorParameters)
          .append(", field(s): ").append(_fields)
          .append(", getter(s): ").append(_getters)
          .append(", setter(s): ").append(_setters)
          ;
        sb.append("]");
        return sb.toString();
    }
    
    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    /**
     * Node used for creating simple linked lists to efficiently store small sets
     * of things.
     */
    private final static class Node<T>
    {
        public final T value;
        public final Node<T> next;

        public final String explicitName;
        public final boolean isMarkedIgnored;
        
        public Node(T v, Node<T> n,
                String explName, boolean ignored)
        {
            value = v;
            next = n;
            // ensure that we'll never have missing names
            if (explName == null) {
                explicitName = null;
            } else {
                explicitName = (explName.length() == 0) ? null : explName;
            }
            isMarkedIgnored = ignored;
        }

        public Node<T> relinked(Node<T> newNext) {
            return new Node<T>(value, newNext, explicitName, isMarkedIgnored);
        }
        
        @Override
        public String toString() {
            String msg = value.toString();
            if (next != null) {
                msg = msg + ", "+next.toString();
            }
            return msg;
        }
    }
}
