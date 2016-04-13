/*
 * Copyright (c) 2016 Evolveum
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
package com.evolveum.midpoint.gui.api.component;

import java.util.Collection;

import com.evolveum.midpoint.web.component.AjaxIconButton;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.web.component.data.Table;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.LinkColumn;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.configuration.PageImportObject;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * @author katkav
 */
public abstract class MainObjectListPanel<T extends ObjectType> extends ObjectListPanel<T> {

    private static final long serialVersionUID = 1L;

    private static final String ID_REFRESH = "refresh";
    private static final String ID_NEW_OBJECT = "newObject";
    private static final String ID_IMPORT_OBJECT = "importObject";
    private static final String ID_BUTTON_BAR = "buttonBar";

    public MainObjectListPanel(String id, Class<T> type, Collection<SelectorOptions<GetOperationOptions>> options, PageBase parentPage) {
        super(id, type, options, parentPage);
    }

    @Override
    protected IColumn<SelectableBean<T>, String> createCheckboxColumn() {
        return new CheckBoxHeaderColumn<>();
    }

    @Override
    protected IColumn<SelectableBean<T>, String> createNameColumn() {
        return new LinkColumn<SelectableBean<T>>(createStringResource("ObjectType.name"),
                ObjectType.F_NAME.getLocalPart(), SelectableBean.F_VALUE + ".name") {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<T>> rowModel) {
                T object = rowModel.getObject().getValue();
                MainObjectListPanel.this.objectDetailsPerformed(target, object);
            }
        };
    }

    protected abstract void objectDetailsPerformed(AjaxRequestTarget target, T object);

    protected abstract void newObjectPerformed(AjaxRequestTarget target);

    @Override
    protected WebMarkupContainer createTableButtonToolbar(String id) {
        return new ButtonBar(id, ID_BUTTON_BAR, this);
    }

    private static class ButtonBar extends Fragment {

        public ButtonBar(String id, String markupId, MainObjectListPanel markupProvider) {
            super(id, markupId, markupProvider);

            initLayout(markupProvider);
        }

        private void initLayout(final MainObjectListPanel mainObjectListPanel) {
            AjaxIconButton refreshIcon = new AjaxIconButton(ID_REFRESH, new Model<>("fa fa-refresh"),
                    mainObjectListPanel.createStringResource("MainObjectListPanel.refresh")) {

                @Override
                public void onClick(AjaxRequestTarget target) {
                    Table table = mainObjectListPanel.getTable();
                    target.add((Component) table);
                }
            };
            add(refreshIcon);

            AjaxIconButton newObjectIcon = new AjaxIconButton(ID_NEW_OBJECT, new Model<>("fa fa-edit"),
                    mainObjectListPanel.createStringResource("MainObjectListPanel.newObject")) {

                @Override
                public void onClick(AjaxRequestTarget target) {
                    mainObjectListPanel.newObjectPerformed(target);
                }
            };
            add(newObjectIcon);

            AjaxIconButton importObject = new AjaxIconButton(ID_IMPORT_OBJECT, new Model<>("fa fa-download"),
                    mainObjectListPanel.createStringResource("MainObjectListPanel.import")) {

                @Override
                public void onClick(AjaxRequestTarget target) {
                    setResponsePage(PageImportObject.class);
                }
            };
            add(importObject);
        }
    }
}
