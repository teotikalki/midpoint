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

package com.evolveum.midpoint.web.component.data;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrderDirection;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.page.PageBase;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.wf.WfDataAccessor;
import com.evolveum.midpoint.wf.WorkflowManager;
import org.apache.commons.lang.Validate;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.SortOrder;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lazyman
 */
public abstract class BaseSortableDataProvider<T extends Serializable> extends SortableDataProvider<T> {

    private static final Trace LOGGER = TraceManager.getTrace(BaseSortableDataProvider.class);
    private Component component;
    private List<T> availableData;
    private ObjectQuery query;

    // after this amount of time cached size will be removed
    // from cache and replaced by new value, time in seconds
    private Map<Serializable, CachedSize> cache = new HashMap<Serializable, CachedSize>();
    private int cacheCleanupThreshold = 60;
    private boolean useCache;

    public BaseSortableDataProvider(Component component) {
        this(component, false);
    }

    public BaseSortableDataProvider(Component component, boolean useCache) {
        Validate.notNull(component, "Component must not be null.");
        this.component = component;
        this.useCache = useCache;

        setSort("name", SortOrder.ASCENDING);
    }

    protected ModelService getModel() {
        MidPointApplication application = (MidPointApplication) MidPointApplication.get();
        return application.getModel();
    }

    protected TaskManager getTaskManager() {
        MidPointApplication application = (MidPointApplication) MidPointApplication.get();
        return application.getTaskManager();
    }

    protected WorkflowManager getWorkflowManager() {
        MidPointApplication application = (MidPointApplication) MidPointApplication.get();
        return application.getWorkflowManager();
    }

    protected WfDataAccessor getWorkflowDataAccessor() {
        MidPointApplication application = (MidPointApplication) MidPointApplication.get();
        return application.getWorkflowManager().getDataAccessor();
    }

    public List<T> getAvailableData() {
        if (availableData == null) {
            availableData = new ArrayList<T>();
        }
        return availableData;
    }

    @Override
    public IModel<T> model(T object) {
        return new Model<T>(object);
    }

    protected PageBase getPage() {
        if (component instanceof PageBase) {
            return (PageBase) component;
        }

        if (component.getPage() instanceof  PageBase) {
            return (PageBase) component.getPage();
        }

        throw new IllegalStateException("Component is not instance of '"+PageBase.class.getName()
                + "' or is not placed on page of that instance.");
    }

    public ObjectQuery getQuery() {
        return query;
    }

    public void setQuery(ObjectQuery query) {
        this.query = query;
    }

    /**
     * Flag method for {@link TablePanel}. If true navigation panel with paging "X to Y from Z results is shown",
     * otherwise only "previous and next" simple paging is used.
     *
     * @return By defaults it returns true.
     */
    public boolean isSizeAvailable() {
        return true;
    }

    protected ObjectPaging createPaging(int first, int count) {
        SortParam sortParam = getSort();
        OrderDirection order;
        if (sortParam.isAscending()) {
            order = OrderDirection.ASCENDING;
        } else {
            order = OrderDirection.DESCENDING;
        }

        return ObjectPaging.createPaging(first, count, sortParam.getProperty(), SchemaConstantsGenerated.NS_COMMON, order);
//        return PagingTypeFactory.createPaging(first, count, order, sortParam.getProperty());
    }

    public void clearCache() {
        cache.clear();
        getAvailableData().clear();
    }

    public int getCacheCleanupThreshold() {
        return cacheCleanupThreshold;
    }

    public void setCacheCleanupThreshold(int cacheCleanupThreshold) {
        Validate.isTrue(cacheCleanupThreshold > 0, "Cache cleanup threshold must be bigger than zero.");
        this.cacheCleanupThreshold = cacheCleanupThreshold;
    }

    @Override
    public int size() {
        LOGGER.trace("begin::size()");
        if (!useCache) {
            return internalSize();
        }

        int size = 0;
        CachedSize cachedSize = getCachedSize(cache);
        if (cachedSize != null) {
            long timestamp = cachedSize.getTimestamp();
            if (System.currentTimeMillis() - timestamp > cacheCleanupThreshold * 1000) {
                //recreate
                size = internalSize();
                addCachedSize(cache, new CachedSize(size, System.currentTimeMillis()));
            } else {
                LOGGER.trace("Size returning from cache.");
                size = cachedSize.getSize();
            }
        } else {
            //recreate
            size = internalSize();
            addCachedSize(cache, new CachedSize(size, System.currentTimeMillis()));
        }

        LOGGER.trace("end::size()");
        return size;
    }

    protected abstract int internalSize();

    protected CachedSize getCachedSize(Map<Serializable, CachedSize> cache) {
        return cache.get(query);
    }

    protected void addCachedSize(Map<Serializable, CachedSize> cache, CachedSize newSize) {
        cache.put(query, newSize);
    }

    public static class CachedSize implements Serializable {

        private long timestamp;
        private int size;

        private CachedSize(int size, long timestamp) {
            this.size = size;
            this.timestamp = timestamp;
        }

        public int getSize() {
            return size;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CachedSize that = (CachedSize) o;

            if (size != that.size) return false;
            if (timestamp != that.timestamp) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + size;
            return result;
        }

        @Override
        public String toString() {
            return "CachedSize(size=" + size + ", timestamp=" + timestamp + ")";
        }
    }
}
