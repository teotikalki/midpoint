/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component.wizard.resource;


import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.ItemPathUtil;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.paging.NavigatorPanel;
import com.evolveum.midpoint.web.component.form.multivalue.MultiValueTextEditPanel;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.component.wizard.WizardStep;
import com.evolveum.midpoint.web.component.wizard.WizardUtil;
import com.evolveum.midpoint.web.component.wizard.resource.component.schemahandling.*;
import com.evolveum.midpoint.web.component.wizard.resource.dto.ResourceObjectTypeDefinitionTypeDto;
import com.evolveum.midpoint.web.component.wizard.resource.dto.SchemaHandlingDto;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnChangeAjaxFormUpdatingBehavior;
import com.evolveum.midpoint.web.page.admin.resources.PageResourceWizard;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.InfoTooltipBehavior;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.string.Strings;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;

import java.util.*;

/**
 *  @author lazyman
 *  @author shood
 */
public class SchemaHandlingStep extends WizardStep {

    private static final Trace LOGGER = TraceManager.getTrace(SchemaHandlingStep.class);

    private static final String DOT_CLASS = SchemaHandlingStep.class.getName() + ".";
    private static final String OPERATION_SAVE_SCHEMA_HANDLING = DOT_CLASS + "saveSchemaHandling";

    private static final String ID_ROWS = "tableRows";
    private static final String ID_ROW_OBJECT_TYPE = "objectTypeRow";
    private static final String ID_LINK_OBJECT_TYPE = "objectTypeLink";
    private static final String ID_NAME_OBJECT_TYPE = "objectTypeName";
    private static final String ID_BUTTON_DELETE_OBJECT_TYPE = "objectTypeDelete";
    private static final String ID_PAGING_OBJECT_TYPE = "objectTypePaging";
    private static final String ID_BUTTON_ADD_OBJECT_TYPE = "objectTypeAddButton";
    private static final String ID_OBJECT_TYPE_EDITOR = "objectTypeConfig";
    private static final String ID_THIRD_ROW_CONTAINER = "thirdRowContainer";
    private static final String ID_EDITOR_NAME = "editorName";
    private static final String ID_EDITOR_KIND = "editorKind";
    private static final String ID_EDITOR_INTENT = "editorIntent";
    private static final String ID_EDITOR_DISPLAY_NAME = "editorDisplayName";
    private static final String ID_EDITOR_DESCRIPTION = "editorDescription";
    private static final String ID_EDITOR_DEFAULT = "editorDefault";
    private static final String ID_EDITOR_BUTTON_DEPENDENCY = "editorDependencyButton";
    private static final String ID_EDITOR_OBJECT_CLASS = "editorObjectClass";
    private static final String ID_EDITOR_ASSIGNMENT_POLICY = "editorAssignmentPolicyRef";
    private static final String ID_EDITOR_BUTTON_ITERATION = "editorIterationButton";
    private static final String ID_EDITOR_BUTTON_PROTECTED = "editorProtectedButton";
    private static final String ID_EDITOR_BUTTON_ACTIVATION = "editorActivationButton";
    private static final String ID_EDITOR_BUTTON_CREDENTIALS = "editorCredentialsButton";
    private static final String ID_EDITOR_ATTRIBUTES = "editorAttributes";
    private static final String ID_EDITOR_ASSOCIATIONS = "editorAssociations";
    private static final String ID_T_KIND = "kindTooltip";
    private static final String ID_T_INTENT = "intentTooltip";
    private static final String ID_T_DEFAULT = "defaultTooltip";
    private static final String ID_T_DEPENDENCY = "dependencyTooltip";
    private static final String ID_T_OBJECT_CLASS = "objectClassTooltip";
    private static final String ID_T_ATTRIBUTES = "attributesTooltip";
    private static final String ID_T_ASSOCIATIONS = "associationsTooltip";
    private static final String ID_T_ASSIGNMENT_POLICY_REF = "assignmentPolicyRefTooltip";
    private static final String ID_T_ITERATION = "iterationTooltip";
    private static final String ID_T_PROTECTED = "protectedTooltip";
    private static final String ID_T_ACTIVATION = "activationTooltip";
    private static final String ID_T_CREDENTIALS = "credentialsTooltip";

    private static final Integer AUTO_COMPLETE_LIST_SIZE = 10;

    @NotNull final private IModel<PrismObject<ResourceType>> resourceModel;
	@NotNull final private PageResourceWizard parentPage;

    private LoadableModel<SchemaHandlingDto> schemaHandlingDtoModel;

    public SchemaHandlingStep(@NotNull final IModel<PrismObject<ResourceType>> resourceModel, @NotNull PageResourceWizard parentPage) {
        super(parentPage);
        this.resourceModel = resourceModel;
		this.parentPage = parentPage;

        schemaHandlingDtoModel = new LoadableModel<SchemaHandlingDto>(false) {
            @Override
            protected SchemaHandlingDto load() {
                return loadSchemaHandlingDto();
            }
        };

        initLayout();
		setOutputMarkupId(true);
    }

	@Override
	protected void onConfigure() {
		if (schemaHandlingDtoModel.isLoaded()) {
			int index = schemaHandlingDtoModel.getObject().getSelectedIndex();
			schemaHandlingDtoModel.reset();
			schemaHandlingDtoModel.getObject().setSelectedIndex(index);
		}
	}

    private SchemaHandlingDto loadSchemaHandlingDto() {

        List<ResourceObjectTypeDefinitionTypeDto> list = new ArrayList<>();
        if (resourceModel.getObject() != null) {
            SchemaHandlingType schemaHandling = getOrCreateSchemaHandling();
			for (ResourceObjectTypeDefinitionType objectType: schemaHandling.getObjectType()) {
				list.add(new ResourceObjectTypeDefinitionTypeDto(objectType));
			}
        }
		SchemaHandlingDto dto = new SchemaHandlingDto(list);
		dto.setObjectClassList(loadResourceObjectClassList(resourceModel, LOGGER, getString("SchemaHandlingStep.message.errorLoadingObjectTypeList")));
        return dto;
    }

    private boolean isAnySelected() {
		return schemaHandlingDtoModel.getObject().getSelected() != null;
    }

    private void initLayout() {
        final ListDataProvider<ResourceObjectTypeDefinitionTypeDto> objectTypeProvider = new ListDataProvider<>(this,
                new PropertyModel<List<ResourceObjectTypeDefinitionTypeDto>>(schemaHandlingDtoModel, SchemaHandlingDto.F_OBJECT_TYPES));

        // first row - object types table
        WebMarkupContainer objectTypesTable = new WebMarkupContainer(ID_ROWS);
        objectTypesTable.setOutputMarkupId(true);
        add(objectTypesTable);

        // second row - object type editor
        WebMarkupContainer objectTypeEditor = new WebMarkupContainer(ID_OBJECT_TYPE_EDITOR);
        objectTypeEditor.setOutputMarkupId(true);
        objectTypeEditor.add(new VisibleEnableBehaviour(){
            @Override
            public boolean isVisible() {
                return isAnySelected();
            }
        });
        add(objectTypeEditor);

        // third row container
        WebMarkupContainer thirdRowContainer = new WebMarkupContainer(ID_THIRD_ROW_CONTAINER);
        thirdRowContainer.setOutputMarkupId(true);
        add(thirdRowContainer);

		// ---------------------- details -----------------------
		// Object type table (list)

        DataView<ResourceObjectTypeDefinitionTypeDto> objectTypeDataView = new DataView<ResourceObjectTypeDefinitionTypeDto>(ID_ROW_OBJECT_TYPE,
                objectTypeProvider, UserProfileStorage.DEFAULT_PAGING_SIZE) {

            @Override
            protected void populateItem(final Item<ResourceObjectTypeDefinitionTypeDto> item) {
                final ResourceObjectTypeDefinitionTypeDto objectType = item.getModelObject();

                AjaxSubmitLink link = new AjaxSubmitLink(ID_LINK_OBJECT_TYPE) {
                    @Override
                    protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                        editObjectTypePerformed(target, objectType);
                    }

					@Override
					protected void onError(AjaxRequestTarget target, Form<?> form) {
						target.add(parentPage.getFeedbackPanel());
					}
				};
                item.add(link);

                Label label = new Label(ID_NAME_OBJECT_TYPE, createObjectTypeDisplayModel(objectType));
                label.setOutputMarkupId(true);
                link.add(label);

                AjaxLink delete = new AjaxLink(ID_BUTTON_DELETE_OBJECT_TYPE) {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        deleteObjectTypePerformed(target, objectType);
                    }
                };
                link.add(delete);

                item.add(AttributeModifier.replace("class", new AbstractReadOnlyModel<String>() {

                    @Override
                    public String getObject() {
						return isSelected(item.getModelObject()) ? "success" : null;
					}
				}));
            }
        };
        objectTypesTable.add(objectTypeDataView);

        NavigatorPanel navigator = new NavigatorPanel(ID_PAGING_OBJECT_TYPE, objectTypeDataView, true);
        navigator.setOutputMarkupPlaceholderTag(true);
        navigator.setOutputMarkupId(true);
        add(navigator);

        AjaxSubmitLink add = new AjaxSubmitLink(ID_BUTTON_ADD_OBJECT_TYPE) {
            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                addObjectTypePerformed(target);
            }
        };
        add(add);

        initObjectTypeEditor(objectTypeEditor);
    }

    private IModel<String> createObjectTypeDisplayModel(final ResourceObjectTypeDefinitionTypeDto objectType){
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                StringBuilder sb = new StringBuilder();

                if (objectType != null && objectType.getObjectType() != null) {
                    ResourceObjectTypeDefinitionType object = objectType.getObjectType();
                    sb.append(object.getDisplayName() != null ? object.getDisplayName() : "- ");
					addKindAndIntent(sb, object.getKind(), object.getIntent());
				}
                return sb.toString();
            }
        };
    }

	public static void addKindAndIntent(StringBuilder sb, ShadowKindType kind, String intent) {
		if (kind != null || intent != null) {
			sb.append(" (");
			sb.append(kind != null ? kind : " - ");
			sb.append(", ");
			sb.append(intent != null ? intent : "- ");
			sb.append(")");
		}
	}

	private void initObjectTypeEditor(WebMarkupContainer editor){
        Label editorLabel = new Label(ID_EDITOR_NAME, new LoadableModel<String>() {

            @Override
            protected String load() {
				ResourceObjectTypeDefinitionTypeDto selected = schemaHandlingDtoModel.getObject().getSelected();
				return selected != null ? selected.getObjectType().getDisplayName() : "";
            }
        });
		editorLabel.setOutputMarkupId(true);
        editor.add(editorLabel);

        DropDownChoice editorKind = new DropDownChoice<>(ID_EDITOR_KIND,
                new PropertyModel<ShadowKindType>(schemaHandlingDtoModel, getExpression(ResourceObjectTypeDefinitionType.F_KIND)),
                WebComponentUtil.createReadonlyModelFromEnum(ShadowKindType.class),
                new EnumChoiceRenderer<ShadowKindType>(this));
		editorKind.add(new UpdateNamesBehaviour());
        editor.add(editorKind);

        TextField editorIntent = new TextField<>(ID_EDITOR_INTENT, new PropertyModel<String>(schemaHandlingDtoModel,
				getExpression(ResourceObjectTypeDefinitionType.F_INTENT)));
		editorIntent.add(new UpdateNamesBehaviour());
        editor.add(editorIntent);

        TextField editorDisplayName = new TextField<>(ID_EDITOR_DISPLAY_NAME, new PropertyModel<String>(schemaHandlingDtoModel,
				getExpression(ResourceObjectTypeDefinitionType.F_DISPLAY_NAME)));
		editorDisplayName.add(new UpdateNamesBehaviour());
        editor.add(editorDisplayName);

        TextArea editorDescription = new TextArea<>(ID_EDITOR_DESCRIPTION, new PropertyModel<String>(schemaHandlingDtoModel,
				getExpression(ResourceObjectTypeDefinitionType.F_DESCRIPTION)));
        editor.add(editorDescription);

        CheckBox editorDefault = new CheckBox(ID_EDITOR_DEFAULT, new PropertyModel<Boolean>(schemaHandlingDtoModel,
                getExpression(ResourceObjectTypeDefinitionType.F_DEFAULT)));
        editor.add(editorDefault);

        AjaxSubmitLink editorDependency = new AjaxSubmitLink(ID_EDITOR_BUTTON_DEPENDENCY) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                dependencyEditPerformed(target);
            }
        };
        addDisabledClassModifier(editorDependency);
        editor.add(editorDependency);

        AutoCompleteSettings autoCompleteSettings = new AutoCompleteSettings();
        autoCompleteSettings.setShowListOnEmptyInput(true);
        autoCompleteSettings.setMaxHeightInPx(200);
        AutoCompleteTextField<String> editorObjectClass = new AutoCompleteTextField<String>(ID_EDITOR_OBJECT_CLASS,
                new PropertyModel<String>(schemaHandlingDtoModel, SchemaHandlingDto.F_OBJECT_CLASS_NAME), autoCompleteSettings) {
            @Override
            protected Iterator<String> getChoices(String input) {
                return getObjectClassChoices(input);
            }
        };
        editorObjectClass.add(new EmptyOnChangeAjaxFormUpdatingBehavior());
        editorObjectClass.add(createObjectClassValidator(new AbstractReadOnlyModel<List<QName>>() {
            @Override
            public List<QName> getObject() {
                return schemaHandlingDtoModel.getObject().getObjectClassList();
            }
        }));
		editorObjectClass.setRequired(true);
		editorObjectClass.setConvertEmptyInputStringToNull(true);
        editor.add(editorObjectClass);

        MultiValueTextEditPanel editorAttributes = new MultiValueTextEditPanel<ResourceAttributeDefinitionType>(ID_EDITOR_ATTRIBUTES,
                new PropertyModel<List<ResourceAttributeDefinitionType>>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_ATTRIBUTE)), false, true){

            @Override
            protected IModel<String> createTextModel(final IModel<ResourceAttributeDefinitionType> model) {
                return new Model<String>() {

                    @Override
                    public String getObject() {
                        if(model == null || model.getObject() == null){
                            return null;
                        }

                        ResourceAttributeDefinitionType attribute = model.getObject();
                        ItemPathType ref = attribute.getRef();
                        if (ref == null || ref.getItemPath().isEmpty()) {
                            return null;
                        }

                        QName name = ItemPathUtil.getOnlySegmentQName(ref);

                        StringBuilder sb = new StringBuilder();
                        String prefix = SchemaConstants.NS_ICF_SCHEMA.equals(name.getNamespaceURI()) ? "icfs" : "ri";
                        sb.append(prefix);
                        sb.append(": ");
                        sb.append(name.getLocalPart());

                        if(attribute.getDisplayName() != null){
                            sb.append(" (").append(attribute.getDisplayName()).append(")");
                        }

                        return sb.toString();
                    }
                };
            }

            @Override
            protected ResourceAttributeDefinitionType createNewEmptyItem(){
                return createEmptyAttributeObject();
            }

            @Override
            protected void editPerformed(AjaxRequestTarget target, ResourceAttributeDefinitionType object){
                editAttributePerformed(target, object);
            }

            @Override
            protected boolean buttonsDisabled(){
                return !isAnySelected();
            }

            @Override
            protected void performRemoveValueHook(AjaxRequestTarget target, ListItem<ResourceAttributeDefinitionType> item) {
                WebMarkupContainer newContainer = new WebMarkupContainer(ID_THIRD_ROW_CONTAINER);
                getThirdRowContainer().replaceWith(newContainer);
                target.add(getThirdRowContainer());
            }
        };
        editor.add(editorAttributes);

        MultiValueTextEditPanel editorAssociations = new MultiValueTextEditPanel<ResourceObjectAssociationType>(ID_EDITOR_ASSOCIATIONS,
                new PropertyModel<List<ResourceObjectAssociationType>>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_ASSOCIATION)), false, true){

            @Override
            protected IModel<String> createTextModel(final IModel<ResourceObjectAssociationType> model) {
                return new Model<String>() {

                    @Override
                    public String getObject() {
                        ResourceObjectAssociationType association = model.getObject();
                        if (association == null || association.getRef() == null) {
                            return null;
                        }

                        StringBuilder sb = new StringBuilder();

                        ItemPathType ref = association.getRef();
                        ItemPath path = ref.getItemPath();
                        if(path != null){
                            sb.append(path.toString());
                        } else {
                            sb.append(association.getRef());
                        }

                        if(association.getDisplayName() != null){
                            sb.append(" (").append(association.getDisplayName()).append(")");
                        }

                        return sb.toString();
                    }
                };
            }

            @Override
            protected ResourceObjectAssociationType createNewEmptyItem() {
                return createEmptyAssociationObject();
            }

            @Override
            protected void editPerformed(AjaxRequestTarget target, ResourceObjectAssociationType object){
                editAssociationPerformed(target, object);
            }

            @Override
            protected boolean buttonsDisabled(){
                return !isAnySelected();
            }

            @Override
            protected void performRemoveValueHook(AjaxRequestTarget target, ListItem<ResourceObjectAssociationType> item) {
                WebMarkupContainer newContainer = new WebMarkupContainer(ID_THIRD_ROW_CONTAINER);
                getThirdRowContainer().replaceWith(newContainer);
                target.add(getThirdRowContainer());
            }
        };
        editor.add(editorAssociations);

        DropDownChoice editorAssignmentPolicyRef = new DropDownChoice<>(ID_EDITOR_ASSIGNMENT_POLICY,
                new PropertyModel<AssignmentPolicyEnforcementType>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_ASSIGNMENT_POLICY_ENFORCEMENT)),
                WebComponentUtil.createReadonlyModelFromEnum(AssignmentPolicyEnforcementType.class),
                new EnumChoiceRenderer<AssignmentPolicyEnforcementType>(this));
        editor.add(editorAssignmentPolicyRef);

        AjaxSubmitLink editorIteration = new AjaxSubmitLink(ID_EDITOR_BUTTON_ITERATION) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                iterationEditPerformed(target);
            }
        };
        addDisabledClassModifier(editorIteration);
        editor.add(editorIteration);

        AjaxSubmitLink editorProtected = new AjaxSubmitLink(ID_EDITOR_BUTTON_PROTECTED) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                protectedEditPerformed(target);
            }
        };
        addDisabledClassModifier(editorProtected);
        editor.add(editorProtected);

        AjaxSubmitLink editorActivation = new AjaxSubmitLink(ID_EDITOR_BUTTON_ACTIVATION) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                activationEditPerformed(target);
            }
        };
        addDisabledClassModifier(editorActivation);
        editor.add(editorActivation);

        AjaxSubmitLink editorCredentials = new AjaxSubmitLink(ID_EDITOR_BUTTON_CREDENTIALS) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                credentialsEditPerformed(target);
            }
        };
        addDisabledClassModifier(editorCredentials);
        editor.add(editorCredentials);

        Label kindTooltip = new Label(ID_T_KIND);
        kindTooltip.add(new InfoTooltipBehavior());
        editor.add(kindTooltip);

        Label intentTooltip = new Label(ID_T_INTENT);
        intentTooltip.add(new InfoTooltipBehavior());
        editor.add(intentTooltip);

        Label defaultTooltip = new Label(ID_T_DEFAULT);
        defaultTooltip.add(new InfoTooltipBehavior());
        editor.add(defaultTooltip);

        Label dependencyTooltip = new Label(ID_T_DEPENDENCY);
        dependencyTooltip.add(new InfoTooltipBehavior());
        editor.add(dependencyTooltip);

        Label objectClassTooltip = new Label(ID_T_OBJECT_CLASS);
        objectClassTooltip.add(new InfoTooltipBehavior());
        editor.add(objectClassTooltip);

        Label attributesTooltip = new Label(ID_T_ATTRIBUTES);
        attributesTooltip.add(new InfoTooltipBehavior());
        editor.add(attributesTooltip);

        Label associationsTooltip = new Label(ID_T_ASSOCIATIONS);
        associationsTooltip.add(new InfoTooltipBehavior());
        editor.add(associationsTooltip);

        Label assignmentPolicyRefTooltip = new Label(ID_T_ASSIGNMENT_POLICY_REF);
        assignmentPolicyRefTooltip.add(new InfoTooltipBehavior());
        editor.add(assignmentPolicyRefTooltip);

        Label iterationTooltip = new Label(ID_T_ITERATION);
        iterationTooltip.add(new InfoTooltipBehavior());
        editor.add(iterationTooltip);

        Label protectedTooltip = new Label(ID_T_PROTECTED);
        protectedTooltip.add(new InfoTooltipBehavior());
        editor.add(protectedTooltip);

        Label activationTooltip = new Label(ID_T_ACTIVATION);
        activationTooltip.add(new InfoTooltipBehavior());
        editor.add(activationTooltip);

        Label credentialsTooltip = new Label(ID_T_CREDENTIALS);
        credentialsTooltip.add(new InfoTooltipBehavior());
        editor.add(credentialsTooltip);
    }

	@NotNull
	private String getExpression(QName property) {
		return SchemaHandlingDto.F_SELECTED + ".objectType." + property.getLocalPart();
	}

	private Iterator<String> getObjectClassChoices(String input) {
        List<QName> resourceObjectClassList = schemaHandlingDtoModel.getObject().getObjectClassList();
        List<String> choices = new ArrayList<>(AUTO_COMPLETE_LIST_SIZE);

        if(Strings.isEmpty(input)){
            for(QName q: resourceObjectClassList){
                choices.add(q.getLocalPart());

                if(choices.size() == AUTO_COMPLETE_LIST_SIZE){
                    break;
                }
            }

            return choices.iterator();
        }

        for(QName q: resourceObjectClassList){
            if(q.getLocalPart().toLowerCase().startsWith(input.toLowerCase())){
                choices.add(q.getLocalPart());

                if(choices.size() == AUTO_COMPLETE_LIST_SIZE){
                    break;
                }
            }
        }

        return choices.iterator();
    }

    private void addDisabledClassModifier(Component component){
        component.add(new AttributeAppender("class", new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                if(!isAnySelected()){
                    return " disabled";
                }

                return null;
            }
        }));
    }

    private Component getObjectListTable(){
        return get(ID_ROWS);
    }

    private Component getObjectTypeEditor(){
        return get(ID_OBJECT_TYPE_EDITOR);
    }

    private Component getThirdRowContainer(){
        return get(ID_THIRD_ROW_CONTAINER);
    }

    private Component getNavigator(){
        return get(ID_PAGING_OBJECT_TYPE);
    }

    private void resetSelected(){
        schemaHandlingDtoModel.getObject().setSelected(null);
    }

    private void insertEmptyThirdRow(){
        getThirdRowContainer().replaceWith(new WebMarkupContainer(ID_THIRD_ROW_CONTAINER));
    }

    private void dependencyEditPerformed(AjaxRequestTarget target){
        WebMarkupContainer newContainer = new ResourceDependencyEditor(ID_THIRD_ROW_CONTAINER,
                new PropertyModel<List<ResourceObjectTypeDependencyType>>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_DEPENDENCY)));
        getThirdRowContainer().replaceWith(newContainer);

        target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR), parentPage.getFeedbackPanel());
    }

    private void iterationEditPerformed(AjaxRequestTarget target){
        WebMarkupContainer newContainer = new ResourceIterationEditor(ID_THIRD_ROW_CONTAINER,
                new PropertyModel<IterationSpecificationType>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_ITERATION)));
        getThirdRowContainer().replaceWith(newContainer);

        target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR), parentPage.getFeedbackPanel());
    }

    private void protectedEditPerformed(AjaxRequestTarget target){
        WebMarkupContainer newContainer = new ResourceProtectedEditor(ID_THIRD_ROW_CONTAINER,
                new PropertyModel<List<ResourceObjectPatternType>>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_PROTECTED)));
        getThirdRowContainer().replaceWith(newContainer);

        target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR), parentPage.getFeedbackPanel());
    }

    private void activationEditPerformed(AjaxRequestTarget target){
        WebMarkupContainer newContainer = new ResourceActivationEditor(ID_THIRD_ROW_CONTAINER,
                new PropertyModel<ResourceActivationDefinitionType>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_ACTIVATION)));
        getThirdRowContainer().replaceWith(newContainer);

        target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR), parentPage.getFeedbackPanel());
    }

    private void credentialsEditPerformed(AjaxRequestTarget target){
        WebMarkupContainer newContainer = new ResourceCredentialsEditor(ID_THIRD_ROW_CONTAINER,
                new PropertyModel<ResourceCredentialsDefinitionType>(schemaHandlingDtoModel,
						getExpression(ResourceObjectTypeDefinitionType.F_CREDENTIALS)));
        getThirdRowContainer().replaceWith(newContainer);

        target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR), parentPage.getFeedbackPanel());
    }

    private void editAttributePerformed(AjaxRequestTarget target, final ResourceAttributeDefinitionType object){
        if(schemaHandlingDtoModel.getObject().getSelected() != null && schemaHandlingDtoModel.getObject().getSelected().getObjectType().getObjectClass() != null){
            WebMarkupContainer newContainer = new ResourceAttributeEditor(ID_THIRD_ROW_CONTAINER, new Model<>(object),
                    schemaHandlingDtoModel.getObject().getSelected().getObjectType(), resourceModel.getObject());
            getThirdRowContainer().replaceWith(newContainer);

            target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR));
        } else {
            warn(getString("SchemaHandlingStep.message.selectObjectClassAttr"));
            getThirdRowContainer().replaceWith(new WebMarkupContainer(ID_THIRD_ROW_CONTAINER));
            target.add(parentPage.getFeedbackPanel(), get(ID_OBJECT_TYPE_EDITOR), getThirdRowContainer());
        }
    }

    private void editAssociationPerformed(AjaxRequestTarget target, ResourceObjectAssociationType object){
        if(schemaHandlingDtoModel.getObject().getSelected() != null && schemaHandlingDtoModel.getObject().getSelected().getObjectType().getObjectClass() != null){
            WebMarkupContainer newContainer = new ResourceAssociationEditor(ID_THIRD_ROW_CONTAINER, new Model<>(object),
                    schemaHandlingDtoModel.getObject().getSelected().getObjectType(), resourceModel.getObject());
            getThirdRowContainer().replaceWith(newContainer);

            target.add(getThirdRowContainer(), get(ID_OBJECT_TYPE_EDITOR), parentPage.getFeedbackPanel());
        } else {
            warn(getString("SchemaHandlingStep.message.selectObjectClassAss"));
            getThirdRowContainer().replaceWith(new WebMarkupContainer(ID_THIRD_ROW_CONTAINER));
            target.add(parentPage.getFeedbackPanel(), get(ID_OBJECT_TYPE_EDITOR), getThirdRowContainer());
        }
    }

    @Override
    public void applyState() {
        savePerformed();
    }

    private void savePerformed() {
        PrismObject<ResourceType> oldResource;
        PrismObject<ResourceType> newResource = resourceModel.getObject();
        Task task = parentPage.createSimpleTask(OPERATION_SAVE_SCHEMA_HANDLING);
        OperationResult result = task.getResult();
        ModelService modelService = parentPage.getModelService();
        ObjectDelta delta;

        removeEmptyContainers(newResource);

        try {
            oldResource = WebModelServiceUtils.loadObject(ResourceType.class, newResource.getOid(), parentPage, task, result);
            if (oldResource == null) {
				throw new IllegalStateException("No resource to apply schema handling to");
			}

			delta = oldResource.diff(newResource);

//                if(LOGGER.isTraceEnabled()){
			LOGGER.info("Applying delta:\n{}", delta.debugDump());
//                }

			Collection<ObjectDelta<? extends ObjectType>> deltas = WebComponentUtil.createDeltaCollection(delta);
			modelService.executeChanges(deltas, null, parentPage.createSimpleTask(OPERATION_SAVE_SCHEMA_HANDLING), result);
			parentPage.resetModels();
        } catch (RuntimeException|CommonException e) {
            LoggingUtils.logException(LOGGER, "Couldn't save schema handling", e);
            result.recordFatalError(getString("SchemaHandlingStep.message.saveError", e));
        } finally {
            result.computeStatusIfUnknown();
        }

        setResult(result);
        if (WebComponentUtil.showResultInPage(result)) {
            parentPage.showResult(result);
        }
    }

    private void editObjectTypePerformed(AjaxRequestTarget target, ResourceObjectTypeDefinitionTypeDto objectType){
		boolean wasAnySelected = isAnySelected();
        schemaHandlingDtoModel.getObject().setSelected(objectType);
        insertEmptyThirdRow();
		if (wasAnySelected) {
			target.add(getObjectListTable(), getNavigator(), getObjectTypeEditor(), getThirdRowContainer());
		} else {
			target.add(this);
		}
    }

    private void deleteObjectTypePerformed(AjaxRequestTarget target, ResourceObjectTypeDefinitionTypeDto objectType){
		ResourceObjectTypeDefinitionType realObjectType = objectType.getObjectType();
		resourceModel.getObject().asObjectable().getSchemaHandling().getObjectType().remove(realObjectType);

        ArrayList<ResourceObjectTypeDefinitionTypeDto> list = (ArrayList<ResourceObjectTypeDefinitionTypeDto>) schemaHandlingDtoModel.getObject().getObjectTypeList();
        list.remove(objectType);

        if (isSelected(objectType)) {
            insertEmptyThirdRow();
            target.add(getThirdRowContainer());
        }

        if(list.isEmpty()){
            insertEmptyThirdRow();
            target.add(getThirdRowContainer());
        }

        target.add(getObjectTypeEditor(), getObjectListTable(), getNavigator());
    }

	private boolean isSelected(ResourceObjectTypeDefinitionTypeDto objectType) {
		if (schemaHandlingDtoModel.getObject().getSelected() != null) {
			return schemaHandlingDtoModel.getObject().getSelected() == objectType;
		} else {
			return false;
		}
	}

	private void addObjectTypePerformed(AjaxRequestTarget target){
        ResourceObjectTypeDefinitionType objectType = new ResourceObjectTypeDefinitionType();
        objectType.setDisplayName(getString("SchemaHandlingStep.label.newObjectType"));
        ResourceObjectTypeDefinitionTypeDto dto = new ResourceObjectTypeDefinitionTypeDto(objectType);

        if (schemaHandlingDtoModel.getObject().getObjectTypeList().isEmpty()){
            objectType.setDefault(true);
        }

        resetSelected();
        schemaHandlingDtoModel.getObject().setSelected(dto);
        schemaHandlingDtoModel.getObject().getObjectTypeList().add(dto);
		getOrCreateSchemaHandling().getObjectType().add(objectType);
        insertEmptyThirdRow();
        //target.add(getObjectListTable(), getNavigator(), getObjectTypeEditor(), getThirdRowContainer());
		target.add(this);
    }

	@NotNull
	private SchemaHandlingType getOrCreateSchemaHandling() {
		PrismObject<ResourceType> resource = resourceModel.getObject();
		try {
			resource.findOrCreateContainer(ResourceType.F_SCHEMA_HANDLING);
		} catch (SchemaException e) {
			throw new IllegalStateException("Couldn't find/create schemaHandling container: " + e.getMessage(), e);
		}
		return resource.asObjectable().getSchemaHandling();
	}

	private void removeEmptyContainers(PrismObject<ResourceType> resourcePrism){
        if(resourcePrism == null){
            return;
        }

        ResourceType resource = resourcePrism.asObjectable();

        if(resource != null && resource.getSchemaHandling() != null){
            SchemaHandlingType schemaHandling = resource.getSchemaHandling();

            for(ResourceObjectTypeDefinitionType objectType: schemaHandling.getObjectType()){

                //Clear obsolete containers from attributes
                List<ResourceAttributeDefinitionType> newAttributeList = new ArrayList<>();
                newAttributeList.addAll(objectType.getAttribute());
                for(ResourceAttributeDefinitionType attribute: objectType.getAttribute()){
                    if(attribute.getRef() == null){
                        newAttributeList.remove(attribute);
                    }
                }
                objectType.getAttribute().clear();
                objectType.getAttribute().addAll(newAttributeList);

                for(ResourceAttributeDefinitionType attr: objectType.getAttribute()){
                    List<MappingType> newInbounds = clearEmptyMappings(attr.getInbound());
                    attr.getInbound().clear();
                    attr.getInbound().addAll(newInbounds);
                }

                //Clear obsolete containers from associations
                List<ResourceObjectAssociationType> newAssociationList = new ArrayList<>();
                newAssociationList.addAll(objectType.getAssociation());
                for(ResourceObjectAssociationType association: objectType.getAssociation()){
                    if(association.getKind() == null){
                        newAssociationList.remove(association);
                    }
                }
                objectType.getAssociation().clear();
                objectType.getAssociation().addAll(newAssociationList);

                for(ResourceObjectAssociationType association: objectType.getAssociation()){
                    List<MappingType> newInbounds = clearEmptyMappings(association.getInbound());
                    association.getInbound().clear();
                    association.getInbound().addAll(newInbounds);
                }

                prepareActivation(objectType.getActivation());
            }
        }
    }

    private List<MappingType> clearEmptyMappings(List<MappingType> list){
        List<MappingType> newList = new ArrayList<>();

        for(MappingType mapping: list){
            if(!WizardUtil.isEmptyMapping(mapping)){
                newList.add(mapping);
            }
        }

        return newList;
    }

    private void prepareActivation(ResourceActivationDefinitionType activation){
        if(activation == null){
            return;
        }

        if(activation.getAdministrativeStatus() != null){
            ResourceBidirectionalMappingType administrativeStatus = activation.getAdministrativeStatus();

            List<MappingType> inbounds = administrativeStatus.getInbound();
            List<MappingType> outbounds = administrativeStatus.getOutbound();

            List<MappingType> newInbounds = prepareActivationMappings(inbounds,
                    ResourceActivationEditor.ADM_STATUS_IN_SOURCE_DEFAULT, ResourceActivationEditor.ADM_STATUS_IN_TARGET_DEFAULT);
            administrativeStatus.getInbound().clear();
            administrativeStatus.getInbound().addAll(newInbounds);

            List<MappingType> newOutbounds = prepareActivationMappings(outbounds,
                    ResourceActivationEditor.ADM_STATUS_OUT_SOURCE_DEFAULT, ResourceActivationEditor.ADM_STATUS_OUT_TARGET_DEFAULT);
            administrativeStatus.getOutbound().clear();
            administrativeStatus.getOutbound().addAll(newOutbounds);

            if(isBidirectionalMappingEmpty(administrativeStatus)){
                activation.setAdministrativeStatus(null);
            }
        }

        if(activation.getValidTo() != null){
            ResourceBidirectionalMappingType validTo = activation.getValidTo();

            List<MappingType> inbounds = validTo.getInbound();
            List<MappingType> outbounds = validTo.getOutbound();

            List<MappingType> newInbounds = prepareActivationMappings(inbounds,
                    ResourceActivationEditor.VALID_TO_IN_SOURCE_DEFAULT, ResourceActivationEditor.VALID_TO_IN_TARGET_DEFAULT);
            validTo.getInbound().clear();
            validTo.getInbound().addAll(newInbounds);

            List<MappingType> newOutbounds = prepareActivationMappings(outbounds,
                    ResourceActivationEditor.VALID_TO_OUT_SOURCE_DEFAULT, ResourceActivationEditor.VALID_TO_OUT_TARGET_DEFAULT);
            validTo.getOutbound().clear();
            validTo.getOutbound().addAll(newOutbounds);

            if(isBidirectionalMappingEmpty(validTo)){
                activation.setValidTo(null);
            }
        }

        if(activation.getValidFrom() != null){
            ResourceBidirectionalMappingType validFrom = activation.getValidFrom();

            List<MappingType> inbounds = validFrom.getInbound();
            List<MappingType> outbounds = validFrom.getOutbound();

            List<MappingType> newInbounds = prepareActivationMappings(inbounds,
                    ResourceActivationEditor.VALID_FROM_IN_SOURCE_DEFAULT, ResourceActivationEditor.VALID_FROM_IN_TARGET_DEFAULT);
            validFrom.getInbound().clear();
            validFrom.getInbound().addAll(newInbounds);

            List<MappingType> newOutbounds = prepareActivationMappings(outbounds,
                    ResourceActivationEditor.VALID_FROM_OUT_SOURCE_DEFAULT, ResourceActivationEditor.VALID_FROM_OUT_TARGET_DEFAULT);
            validFrom.getOutbound().clear();
            validFrom.getOutbound().addAll(newOutbounds);

            if(isBidirectionalMappingEmpty(validFrom)){
                activation.setValidFrom(null);
            }
        }

        if(activation.getExistence() != null){
            ResourceBidirectionalMappingType existence = activation.getExistence();

            List<MappingType> inbounds = existence.getInbound();
            List<MappingType> newInbounds = new ArrayList<>();

            for(MappingType inbound: inbounds){
                if(WizardUtil.isEmptyMapping(inbound)){
                    continue;
                }

                if(inbound.getSource().size() == 0 && compareItemPath(inbound.getSource().get(0).getPath(), ResourceActivationEditor.EXISTENCE_DEFAULT_SOURCE)){
                    newInbounds.add(new MappingType());
                    continue;
                }

                newInbounds.add(inbound);
            }

            existence.getInbound().clear();
            existence.getInbound().addAll(newInbounds);

            List<MappingType> outbounds = existence.getOutbound();
            List<MappingType> newOutbounds = new ArrayList<>();

            for(MappingType outbound: outbounds){
                if(!WizardUtil.isEmptyMapping(outbound)){
                    newOutbounds.add(outbound);
                }
            }

            existence.getOutbound().clear();
            existence.getOutbound().addAll(newOutbounds);

            if(isBidirectionalMappingEmpty(existence)){
                activation.setExistence(null);
            }
        }
    }

    private boolean isBidirectionalMappingEmpty(ResourceBidirectionalMappingType mapping){
        return mapping.getFetchStrategy() == null && mapping.getInbound().isEmpty() && mapping.getOutbound().isEmpty();

    }

    private List<MappingType> prepareActivationMappings(List<MappingType> list, String defaultSource, String defaultTarget){
        List<MappingType> newMappings = new ArrayList<>();

        for(MappingType mapping: list){
            if(WizardUtil.isEmptyMapping(mapping)){
                continue;
            }

            if(mapping.getTarget() != null){
                if(compareItemPath(mapping.getTarget().getPath(), defaultTarget)){
                    mapping.setTarget(null);
                }
            }

            if(mapping.getSource().size() == 1){
                if(compareItemPath(mapping.getSource().get(0).getPath(), defaultSource)){
                    mapping.getSource().clear();
                }
            }

            newMappings.add(mapping);
        }

        return newMappings;
    }

    private boolean compareItemPath(ItemPathType itemPath, String comparePath){
        if(itemPath != null && itemPath.getItemPath() != null){
            if(comparePath.equals(itemPath.getItemPath().toString())){
                return true;
            }
        }

        return false;
    }

    private ResourceObjectAssociationType createEmptyAssociationObject(){
        ResourceObjectAssociationType association = new ResourceObjectAssociationType();
        association.setTolerant(true);
        return association;
    }

    private ResourceAttributeDefinitionType createEmptyAttributeObject(){
        ResourceAttributeDefinitionType attribute = new ResourceAttributeDefinitionType();
        attribute.setTolerant(true);
        return attribute;
    }

	private class UpdateNamesBehaviour extends EmptyOnChangeAjaxFormUpdatingBehavior {
		@Override
		protected void onUpdate(AjaxRequestTarget target) {
			target.add(getObjectListTable(), getObjectTypeEditor().get(ID_EDITOR_NAME));
		}
	}
}
