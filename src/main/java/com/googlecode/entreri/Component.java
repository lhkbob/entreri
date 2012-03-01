package com.googlecode.entreri;

/**
 * <p>
 * Component represents a grouping of reusable and related states that are added
 * to an {@link Entity}. The specific state of a component is stored and defined
 * in {@link ComponentData} implementations. This separation is to support fast
 * iteration using local memory. All of the component data is packed into
 * buffers or arrays for cache locality.
 * </p>
 * <p>
 * Component instances represent the identity of the conceptual components,
 * while instances of ComponentData can be configured to read and write to
 * specific components. ComponentData's can change which component they
 * reference multiple times throughout their life time.
 * </p>
 * 
 * @author Michael Ludwig
 */
public final class Component<T extends ComponentData<T>> {
    private final ComponentRepository<T> owner;
    
    int index;

    /**
     * Create a new Component stored in the given ComponentRepository, at the given
     * array position within the ComponentRepository.
     * 
     * @param owner The ComponentRepository owner
     * @param index The index within the owner
     */
    Component(ComponentRepository<T> owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    /**
     * Get a ComponentData instance that can be used to manipulate the state of
     * this component. This is a convenience for allocating a new ComponentData
     * instance and assigning it to this component. For tight loops, it is
     * better to allocate a single ComponentData instance and use its
     * {@link ComponentData#set(Component) set} method.
     * 
     * @return A ComponentData to access this component's state, or null if the
     *         component is not live
     */
    public T getData() {
        T data = getEntitySystem().createDataInstance(getTypeId());
        if (data.set(this))
            return data;
        else
            return null; 
    }

    /**
     * @return True if the component is still attached to an entity in the
     *         entity system, or false if it or its entity has been removed
     */
    public boolean isLive() {
        return index != 0;
    }

    /**
     * @return True if this component is enabled, or false if it is disabled and
     *         will appear as though it doesn't exist under default behavior
     */
    public boolean isEnabled() {
        // if isLive() returns false, index references the 0th index, which
        // just contains garbage
        return owner.isEnabled(index);
    }

    /**
     * <p>
     * Set whether or not this component is enabled. If a component is disabled,
     * default usage will cause it to appear as the component has been removed.
     * It will not be returned from {@link Entity#get(TypeId)} or be included in
     * iterator results using {@link ComponentIterator}.
     * </p>
     * <p>
     * Disabling and enabling components can be a more efficient way to simulate
     * the adding and removing of components, because it does not remove or
     * require the allocation of new data.
     * </p>
     * 
     * @param enable True if the component is to be enabled
     */
    public void setEnabled(boolean enable) {
        // if isLive() returns false, index references the 0th index, which
        // just contains garbage so this setter is safe
        owner.setEnabled(index, enable);
    }

    /**
     * Get the entity that this component is attached to. If the component has
     * been removed from the entity, or is otherwise not live, this will return
     * null.
     * 
     * @return The owning entity, or null
     */
    public Entity getEntity() {
        // if isLive() returns false, then the entity index will also be 0,
        // so getEntityByIndex() returns null, which is expected
        int entityIndex = owner.getEntityIndex(index);
        return owner.getEntitySystem().getEntityByIndex(entityIndex);
    }
    
    /**
     * @return The EntitySystem that created this component
     */
    public EntitySystem getEntitySystem() {
        return owner.getEntitySystem();
    }
    
    /**
     * @return The TypeId of the ComponentData for this Component
     */
    public TypeId<T> getTypeId() {
        return owner.getTypeId();
    }
    
    /**
     * @return The ComponentRepository owning this Component
     */
    ComponentRepository<T> getRepository() {
        return owner;
    }
}
