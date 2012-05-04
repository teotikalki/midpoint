/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.model.synchronizer;

import com.evolveum.midpoint.common.refinery.RefinedAccountDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.ResourceAccountType;
import com.evolveum.midpoint.common.valueconstruction.ValueConstruction;
import com.evolveum.midpoint.model.AccountSyncContext;
import com.evolveum.midpoint.model.PolicyDecision;
import com.evolveum.midpoint.model.SyncContext;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.PropertyPath;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * This processor consolidate delta set triples acquired from account sync context and transforms them to
 * property deltas. It considers also property deltas from sync, which already happened.
 *
 * @author lazyman
 */
@Component
public class ConsolidationProcessor {

    public static final String PROCESS_CONSOLIDATION = ConsolidationProcessor.class.getName() + ".consolidateValues";
    private static final Trace LOGGER = TraceManager.getTrace(ReconciliationProcessor.class);

    /**
     * Converts delta set triples to a secondary account deltas.
     */
    void consolidateValues(SyncContext context, AccountSyncContext accCtx, OperationResult result) throws SchemaException,
            ExpressionEvaluationException {
    		//todo filter changes which were already in account sync delta

        //account was deleted, no changes are needed.
        if (wasAccountDeleted(accCtx)) {
            dropAllAccountDelta(accCtx);
            return;
        }

        PolicyDecision policyDecision = accCtx.getPolicyDecision();

        if (policyDecision == PolicyDecision.ADD) {
            consolidateValuesAddAccount(context, accCtx, result);
        } else if (policyDecision == PolicyDecision.KEEP) {
            consolidateValuesModifyAccount(context, accCtx, result);
        } else if (policyDecision == PolicyDecision.DELETE) {
            consolidateValuesDeleteAccount(context, accCtx, result);
        } else {
            // This is either UNLINK or null, both are in fact the same as KEEP
            consolidateValuesModifyAccount(context, accCtx, result);
        }
    }

    private void dropAllAccountDelta(AccountSyncContext accContext) {
        accContext.setAccountPrimaryDelta(null);
        accContext.setAccountSecondaryDelta(null);
    }

    private boolean wasAccountDeleted(AccountSyncContext accContext) {
        ObjectDelta<AccountShadowType> delta = accContext.getAccountSyncDelta();
        if (delta != null && ChangeType.DELETE.equals(delta.getChangeType())) {
            return true;
        }

        return false;
    }

    private ObjectDelta<AccountShadowType> consolidateValuesToModifyDelta(SyncContext context,
            AccountSyncContext accCtx,
            boolean addUnchangedValues, OperationResult result) throws SchemaException, ExpressionEvaluationException {

    	Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedAttributes = sqeezeAttributes(accCtx); 
    	accCtx.setSqueezedAttributes(squeezedAttributes);
        
        ResourceAccountType rat = accCtx.getResourceAccountType();
        ObjectDelta<AccountShadowType> objectDelta = new ObjectDelta<AccountShadowType>(AccountShadowType.class, ChangeType.MODIFY);
        objectDelta.setOid(accCtx.getOid());

        RefinedAccountDefinition rAccount = context.getRefinedAccountDefinition(rat);
        if (rAccount == null) {
            LOGGER.error("Definition for account type {} not found in the context, but it should be there, dumping context:\n{}", rat, context.dump());
            throw new IllegalStateException("Definition for account type " + rat + " not found in the context, but it should be there");
        }

        PropertyPath parentPath = new PropertyPath(SchemaConstants.I_ATTRIBUTES);

        for (Map.Entry<QName, DeltaSetTriple<PropertyValueWithOrigin>> entry : squeezedAttributes.entrySet()) {
            QName attributeName = entry.getKey();
            DeltaSetTriple<PropertyValueWithOrigin> triple = entry.getValue();

            PropertyDelta<?> propDelta = null;

            LOGGER.trace("Consolidating (modify) account {}, attribute {}", rat, attributeName);

            PrismContainer<?> attributesPropertyContainer = null;
            if (accCtx.getAccountNew() != null) {
                attributesPropertyContainer = accCtx.getAccountNew().findContainer(SchemaConstants.I_ATTRIBUTES);
            }

            Collection<PrismPropertyValue<?>> allValues = collectAllValues(triple);
            for (PrismPropertyValue<?> value : allValues) {
                Collection<PropertyValueWithOrigin> zeroPvwos =
                        collectPvwosFromSet(value, triple.getZeroSet());
                if (!zeroPvwos.isEmpty() && !addUnchangedValues) {
                    // Value unchanged, nothing to do
                    LOGGER.trace("Value {} unchanged, doing nothing", value);
                    continue;
                }
                Collection<PropertyValueWithOrigin> plusPvwos =
                        collectPvwosFromSet(value, triple.getPlusSet());
                Collection<PropertyValueWithOrigin> minusPvwos =
                        collectPvwosFromSet(value, triple.getMinusSet());
                if (!plusPvwos.isEmpty() && !minusPvwos.isEmpty()) {
                    // Value added and removed. Ergo no change.
                    LOGGER.trace("Value {} added and removed, doing nothing", value);
                    continue;
                }
                if (propDelta == null) {
                	RefinedAttributeDefinition attributeDefinition = rAccount.findAttributeDefinition(attributeName);
                    propDelta = new PropertyDelta(parentPath, attributeName, attributeDefinition);
                }

                boolean initialOnly = true;
                ValueConstruction<?> exclusiveVc = null;
                Collection<PropertyValueWithOrigin> pvwosToAdd = null;
                if (addUnchangedValues) {
                    pvwosToAdd = MiscUtil.union(zeroPvwos, plusPvwos);
                } else {
                    pvwosToAdd = plusPvwos;
                }

                if (!pvwosToAdd.isEmpty()) {
                    for (PropertyValueWithOrigin pvwoToAdd : pvwosToAdd) {
                        ValueConstruction<?> vc = pvwoToAdd.getValueConstruction();
                        if (!vc.isInitial()) {
                            initialOnly = false;
                        }
                        if (vc.isExclusive()) {
                            if (exclusiveVc == null) {
                                exclusiveVc = vc;
                            } else {
                                String message = "Exclusion conflict in account " + rat + ", attribute " + attributeName +
                                        ", conflicting constructions: " + exclusiveVc + " and " + vc;
                                LOGGER.error(message);
                                throw new ExpressionEvaluationException(message);
                            }
                        }
                    }
                    if (initialOnly) {
                        if (attributesPropertyContainer != null) {
                            PrismProperty attributeNew = attributesPropertyContainer.findProperty(attributeName);
                            if (attributeNew != null && !attributeNew.isEmpty()) {
                                // There is already a value, skip this
                                LOGGER.trace("Value {} is initial and the attribute already has a value, skipping it", value);
                                continue;
                            }
                        }
                    }
                    LOGGER.trace("Value {} added", value);
                    propDelta.addValueToAdd((PrismPropertyValue)value);
                }

                if (!minusPvwos.isEmpty()) {
                    LOGGER.trace("Value {} deleted", value);
                    propDelta.addValueToDelete((PrismPropertyValue)value);
                }
            }

            propDelta = consolidateWithSync(accCtx, propDelta);

            if (propDelta != null) {
            	propDelta.simplify();
                objectDelta.addModification(propDelta);
            }
        }

        return objectDelta;
    }

	private void consolidateValuesAddAccount(SyncContext context, AccountSyncContext accCtx,
            OperationResult result) throws SchemaException, ExpressionEvaluationException {

        ObjectDelta<AccountShadowType> modifyDelta = consolidateValuesToModifyDelta(context, accCtx, true, result);
        ObjectDelta<AccountShadowType> accountSecondaryDelta = accCtx.getAccountSecondaryDelta();
        if (accountSecondaryDelta != null) {
            accountSecondaryDelta.merge(modifyDelta);
        } else {
            if (accCtx.getAccountPrimaryDelta() == null) {
                ObjectDelta<AccountShadowType> addDelta = new ObjectDelta<AccountShadowType>(AccountShadowType.class, ChangeType.ADD);
                RefinedAccountDefinition rAccount = context.getRefinedAccountDefinition(accCtx.getResourceAccountType());

                if (rAccount == null) {
                    LOGGER.error("Definition for account type {} not found in the context, but it should be there, dumping context:\n{}", accCtx.getResourceAccountType(), context.dump());
                    throw new IllegalStateException("Definition for account type " + accCtx.getResourceAccountType() + " not found in the context, but it should be there");
                }
                PrismObject<AccountShadowType> newAccount = rAccount.createBlankShadow();
                addDelta.setObjectToAdd(newAccount);

                addDelta.merge(modifyDelta);
                accCtx.setAccountSecondaryDelta(addDelta);
            } else {
                accCtx.setAccountSecondaryDelta(modifyDelta);
            }
        }
    }

    private void consolidateValuesModifyAccount(SyncContext context, AccountSyncContext accCtx,
            OperationResult result) throws SchemaException, ExpressionEvaluationException {

        ObjectDelta<AccountShadowType> modifyDelta = consolidateValuesToModifyDelta(context, accCtx, false, result);
        ObjectDelta<AccountShadowType> accountSecondaryDelta = accCtx.getAccountSecondaryDelta();
        if (accountSecondaryDelta != null) {
            if (!modifyDelta.isEmpty()) {
                accountSecondaryDelta.merge(modifyDelta);
            }
        } else {
            accCtx.setAccountSecondaryDelta(modifyDelta);
        }
    }

    private void consolidateValuesDeleteAccount(SyncContext context, AccountSyncContext accCtx,
            OperationResult result) {
        ObjectDelta<AccountShadowType> deleteDelta = new ObjectDelta<AccountShadowType>(AccountShadowType.class, ChangeType.DELETE);
        deleteDelta.setOid(accCtx.getOid());
        accCtx.setAccountSecondaryDelta(deleteDelta);
    }

    /**
     * This method checks {@link com.evolveum.midpoint.prism.delta.PropertyDelta} created during consolidation with
     * account sync deltas. If changes from property delta are in account sync deltas than they must be removed,
     * because they already had been applied (they came from sync, already happened).
     *
     * @param accCtx current account sync context
     * @param delta  new delta created during consolidation process
     * @return method return updated delta, or null if delta was empty after filtering (removing unnecessary values).
     */
    private PropertyDelta consolidateWithSync(AccountSyncContext accCtx, PropertyDelta delta) {
        if (delta == null) {
            return null;
        }

        ObjectDelta<AccountShadowType> syncDelta = accCtx.getAccountSyncDelta();
        if (syncDelta == null) {
            return consolidateWithSyncAbsolute(accCtx, delta);
        }

        PropertyDelta alreadyDoneDelta = syncDelta.findPropertyDelta(delta.getPath());
        if (alreadyDoneDelta == null) {
            return delta;
        }

        cleanupValues(delta.getValuesToAdd(), alreadyDoneDelta);
        cleanupValues(delta.getValuesToDelete(), alreadyDoneDelta);

        if (delta.getValues(Object.class).isEmpty()) {
            return null;
        }

        return delta;
    }

    /**
     * This method consolidate property delta against account absolute state which came from sync (not as delta)
     *
     * @param accCtx
     * @param delta
     * @return method return updated delta, or null if delta was empty after filtering (removing unnecessary values).
     */
    private PropertyDelta consolidateWithSyncAbsolute(AccountSyncContext accCtx, PropertyDelta delta) {
        if (delta == null || accCtx.getAccountOld() == null) {
            return delta;
        }

        PrismObject<AccountShadowType> absoluteAccountState = accCtx.getAccountOld();
        PrismProperty absoluteProperty = absoluteAccountState.findProperty(delta.getPath());
        if (absoluteProperty == null) {
            return delta;
        }

        cleanupAbsoluteValues(delta.getValuesToAdd(), true, absoluteProperty);
        cleanupAbsoluteValues(delta.getValuesToDelete(), false, absoluteProperty);

        if (delta.getValues(Object.class).isEmpty()) {
            return null;
        }

        return delta;
    }

    /**
     * Method removes values from property delta values list (first parameter).
     *
     * @param values   collection with {@link PrismPropertyValue} objects to add or delete (from {@link PropertyDelta}
     * @param adding   if true we removing {@link PrismPropertyValue} from {@link Collection} values parameter if they
     *                 already are in {@link PrismProperty} parameter. Otherwise we're removing {@link PrismPropertyValue}
     *                 from {@link Collection} values parameter if they already are not in {@link PrismProperty} parameter.
     * @param property property with absolute state
     */
    private void cleanupAbsoluteValues(Collection<PrismPropertyValue<Object>> values, boolean adding, PrismProperty property) {
        if (values == null) {
            return;
        }

        Iterator<PrismPropertyValue<Object>> iterator = values.iterator();
        while (iterator.hasNext()) {
            PrismPropertyValue<Object> value = iterator.next();
            if (adding && property.hasRealValue(value)) {
                iterator.remove();
            }

            if (!adding && !property.hasRealValue(value)) {
                iterator.remove();
            }
        }
    }

    /**
     * Simple util method which checks property values against already done delta from sync. See method
     * {@link ConsolidationProcessor#consolidateWithSync(com.evolveum.midpoint.model.AccountSyncContext,
     * com.evolveum.midpoint.prism.delta.PropertyDelta)}.
     *
     * @param values           collection which has to be filtered
     * @param alreadyDoneDelta already applied delta from sync
     */
    private void cleanupValues(Collection<PrismPropertyValue<Object>> values, PropertyDelta alreadyDoneDelta) {
        if (values == null) {
            return;
        }

        Iterator<PrismPropertyValue<Object>> iterator = values.iterator();
        while (iterator.hasNext()) {
            PrismPropertyValue<Object> valueToAdd = iterator.next();
            if (alreadyDoneDelta.isRealValueToAdd(valueToAdd)) {
                iterator.remove();
            }
        }
    }

    private Collection<PrismPropertyValue<?>> collectAllValues(DeltaSetTriple<PropertyValueWithOrigin> triple) {
        Collection<PrismPropertyValue<?>> allValues = new HashSet<PrismPropertyValue<?>>();
        collectAllValuesFromSet(allValues, triple.getZeroSet());
        collectAllValuesFromSet(allValues, triple.getPlusSet());
        collectAllValuesFromSet(allValues, triple.getMinusSet());
        return allValues;
    }

    private void collectAllValuesFromSet(Collection<PrismPropertyValue<?>> allValues,
            Collection<PropertyValueWithOrigin> collection) {
        if (collection == null) {
            return;
        }
        for (PropertyValueWithOrigin pvwo : collection) {
        	PrismPropertyValue<?> pval = pvwo.getPropertyValue();
        	if (!PrismValue.containsRealValue(allValues, pval)) {
        		allValues.add(pval);
        	}
        }
    }

    private Collection<PropertyValueWithOrigin> collectPvwosFromSet(PrismPropertyValue<?> pvalue,
            Collection<PropertyValueWithOrigin> deltaSet) {
    	Collection<PropertyValueWithOrigin> pvwos = new ArrayList<PropertyValueWithOrigin>();
        for (PropertyValueWithOrigin setPvwo : deltaSet) {
        	if (setPvwo.equalsRealValue(pvalue)) {
        		pvwos.add(setPvwo);
        	}
        }
        return pvwos;
    }
    
	private Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> sqeezeAttributes(AccountSyncContext accCtx) {
		Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap = new HashMap<QName, DeltaSetTriple<PropertyValueWithOrigin>>();
		if (accCtx.getAccountConstructionDeltaSetTriple() != null) {
			sqeezeAttributesFromAccountConstructionTriple(squeezedMap, accCtx.getAccountConstructionDeltaSetTriple());		
		}
		if (accCtx.getOutboundAccountConstruction() != null) {
			// The plus-minus-zero status of outbound account construction is determined by the type of account delta
			if (accCtx.isAdd()) {
				sqeezeAttributesFromAccountConstructionNonminusToPlus(squeezedMap, accCtx.getOutboundAccountConstruction());
			} else if (accCtx.isDelete()) {
				sqeezeAttributesFromAccountConstructionNonminusToMinus(squeezedMap, accCtx.getOutboundAccountConstruction());
			} else {
				sqeezeAttributesFromAccountConstruction(squeezedMap, accCtx.getOutboundAccountConstruction());
			}
		}
		return squeezedMap;
	}

	private void sqeezeAttributesFromAccountConstructionTriple(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			PrismValueDeltaSetTriple<PrismPropertyValue<AccountConstruction>> accountConstructionDeltaSetTriple) {
		// Zero account constructions go normally, plus to plus, minus to minus
		sqeezeAttributesFromAccountConstructionSet(squeezedMap, accountConstructionDeltaSetTriple.getZeroSet());
		// Plus accounts: zero and plus values go to plus
		sqeezeAttributesFromAccountConstructionSetNonminusToPlus(squeezedMap, accountConstructionDeltaSetTriple.getPlusSet());
		// Minus accounts: zero and plus values go to minus
		sqeezeAttributesFromAccountConstructionSetNonminusToMinus(squeezedMap, accountConstructionDeltaSetTriple.getMinusSet());
	}

	private void sqeezeAttributesFromAccountConstructionSet(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			Collection<PrismPropertyValue<AccountConstruction>> accountConstructionSet) {
		if (accountConstructionSet == null) {
			return;
		}
		for (PrismPropertyValue<AccountConstruction> accountConstruction: accountConstructionSet) {
			sqeezeAttributesFromAccountConstruction(squeezedMap, accountConstruction.getValue());
		}
	}
	
	private void sqeezeAttributesFromAccountConstructionSetNonminusToPlus(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			Collection<PrismPropertyValue<AccountConstruction>> accountConstructionSet) {
		if (accountConstructionSet == null) {
			return;
		}
		for (PrismPropertyValue<AccountConstruction> accountConstruction: accountConstructionSet) {
			sqeezeAttributesFromAccountConstructionNonminusToPlus(squeezedMap, accountConstruction.getValue());
		}
	}
	
	private void sqeezeAttributesFromAccountConstructionSetNonminusToMinus(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			Collection<PrismPropertyValue<AccountConstruction>> accountConstructionSet) {
		if (accountConstructionSet == null) {
			return;
		}
		for (PrismPropertyValue<AccountConstruction> accountConstruction: accountConstructionSet) {
			sqeezeAttributesFromAccountConstructionNonminusToMinus(squeezedMap, accountConstruction.getValue());
		}
	}

	private void sqeezeAttributesFromAccountConstruction(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			AccountConstruction accountConstruction) {
		for (ValueConstruction<? extends PrismPropertyValue<?>> vc: accountConstruction.getAttributeConstructions()) {
			DeltaSetTriple<PropertyValueWithOrigin> squeezeTriple = getSqueezeMapTriple(squeezedMap, vc.getItemName());
			PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> vcTriple = vc.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getZeroSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getPlusSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getMinusSet(), squeezeTriple.getMinusSet(), vc, accountConstruction);
		}
	}
	
	private void sqeezeAttributesFromAccountConstructionNonminusToPlus(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			AccountConstruction accountConstruction) {
		for (ValueConstruction<? extends PrismPropertyValue<?>> vc: accountConstruction.getAttributeConstructions()) {
			DeltaSetTriple<PropertyValueWithOrigin> squeezeTriple = getSqueezeMapTriple(squeezedMap, vc.getItemName());
			PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> vcTriple = vc.getOutputTriple();
			if (vcTriple == null) {
				continue;
			}
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getPlusSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getPlusSet(), vc, accountConstruction);
			// Ignore minus set
		}
	}

	private void sqeezeAttributesFromAccountConstructionNonminusToMinus(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap,
			AccountConstruction accountConstruction) {
		for (ValueConstruction<? extends PrismPropertyValue<?>> vc: accountConstruction.getAttributeConstructions()) {
			DeltaSetTriple<PropertyValueWithOrigin> squeezeTriple = getSqueezeMapTriple(squeezedMap, vc.getItemName());
			PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> vcTriple = vc.getOutputTriple();
			convertSqueezeSet(vcTriple.getZeroSet(), squeezeTriple.getMinusSet(), vc, accountConstruction);
			convertSqueezeSet(vcTriple.getPlusSet(), squeezeTriple.getMinusSet(), vc, accountConstruction);
			// Ignore minus set
		}
	}

	private void convertSqueezeSet(Collection<? extends PrismPropertyValue<?>> fromSet,
			Collection<PropertyValueWithOrigin> toSet,
			ValueConstruction<? extends PrismPropertyValue<?>> valueConstruction, AccountConstruction accountConstruction) {
		for (PrismPropertyValue<?> from: fromSet) {
			PropertyValueWithOrigin pvwo = new PropertyValueWithOrigin(from, valueConstruction, accountConstruction);
			toSet.add(pvwo);
		}
	}

	private DeltaSetTriple<PropertyValueWithOrigin> getSqueezeMapTriple(
			Map<QName, DeltaSetTriple<PropertyValueWithOrigin>> squeezedMap, QName itemName) {
		DeltaSetTriple<PropertyValueWithOrigin> triple = squeezedMap.get(itemName);
		if (triple == null) {
			triple = new DeltaSetTriple<PropertyValueWithOrigin>();
			squeezedMap.put(itemName, triple);
		}
		return triple;
	}

}
