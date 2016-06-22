/*
 * Copyright (c) 2010-2016 Evolveum
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

import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.ColumnUtils;
import com.evolveum.midpoint.web.component.data.column.LinkColumn;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

public abstract class PopupObjectListPanel<T extends ObjectType> extends ObjectListPanel<T> {
	private static final long serialVersionUID = 1L;

	/**
	 * @param defaultType specifies type of the object that will be selected by default
	 */
	public PopupObjectListPanel(String id, Class<T> defaultType, boolean multiselect, PageBase parentPage) {
		super(id, defaultType, multiselect, parentPage);
		
	}

	@Override
	protected IColumn<SelectableBean<T>, String> createCheckboxColumn() {
		if (isMultiselect()) {
			return new CheckBoxHeaderColumn<SelectableBean<T>>() {
				private static final long serialVersionUID = 1L;
				
				@Override
				protected void onUpdateRow(AjaxRequestTarget target, DataTable table, IModel<SelectableBean<T>> rowModel) {
					super.onUpdateRow(target, table, rowModel);
					onUpdateCheckbox(target);
				};
				
				@Override
				protected void onUpdateHeader(AjaxRequestTarget target, boolean selected, DataTable table) {
					super.onUpdateHeader(target, selected, table);
					onUpdateCheckbox(target);
				}
			};
		}
		return null;
	}

	@Override
	protected IColumn<SelectableBean<T>, String> createNameColumn() {
		if (!isMultiselect()) {
			return new LinkColumn<SelectableBean<T>>(createStringResource("ObjectType.name"),
					ObjectType.F_NAME.getLocalPart(), SelectableBean.F_VALUE + ".name") {
				private static final long serialVersionUID = 1L;

				@Override
				public void onClick(AjaxRequestTarget target, IModel<SelectableBean<T>> rowModel) {
					T object = rowModel.getObject().getValue();
					onSelectPerformed(target, object);

				}
			};
		}

		else {
			return new PropertyColumn(createStringResource("userBrowserDialog.name"),
					ObjectType.F_NAME.getLocalPart(), SelectableBean.F_VALUE + ".name");
		}
	}

	@Override
	protected List<IColumn<SelectableBean<T>, String>> createColumns() {
		return ColumnUtils.getDefaultColumns(getType());
	}
	
	protected void onSelectPerformed(AjaxRequestTarget target, T object){
		
	}
	
	@Override
	protected List<InlineMenuItem> createInlineMenu() {
		return null;
	}
	
	protected void onUpdateCheckbox(AjaxRequestTarget target){
		
	}

}
