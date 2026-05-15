package com.consid.automation.camunda.internal.model;

/**
 * The type axis of a {@link FieldDescriptor}. Each permitted implementation
 * carries the constraints that are meaningful for that type and nothing else.
 * Replaces the old "FieldType enum + four parallel *Constraints records, three
 * of which are always NONE" model.
 *
 * <p>The expression builder dispatches on this sealed type via pattern matching;
 * adding a new type family is one new permit plus one new switch arm.
 */
public sealed interface TypeInfo
    permits StringTypeInfo, NumberTypeInfo, BooleanTypeInfo,
            ArrayTypeInfo, ObjectTypeInfo, UnknownTypeInfo {
}
