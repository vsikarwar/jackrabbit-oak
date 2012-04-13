/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr.security.user;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.oak.jcr.NodeImpl;
import org.apache.jackrabbit.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * AuthorizableImpl...
 */
abstract class AuthorizableImpl implements Authorizable {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(AuthorizableImpl.class);

    static final String NT_REP_AUTHORIZABLE = "rep:Authorizable";
    static final String NT_REP_USER = "rep:User";
    static final String NT_REP_GROUP = "rep:Group";
    static final String REP_PRINCIPAL_NAME = "rep:principalName";
    static final String REP_PASSWORD = "rep:password";
    static final String REP_DISABLED = "rep:disabled";
    static final String REP_MEMBERS = "rep:members";
    static final String REP_IMPERSONATORS = "rep:impersonators";

    private final NodeImpl node;
    private final UserManagerImpl userManager;

    private int hashCode;

    AuthorizableImpl(NodeImpl node, UserManagerImpl userManager) {
        this.node = node;
        this.userManager = userManager;
    }

    //-------------------------------------------------------< Authorizable >---
    @Override
    public String getID() throws RepositoryException {
        // TODO
        return null;
    }

    @Override
    public Iterator<Group> declaredMemberOf() throws RepositoryException {
        // TODO
        return null;
    }

    @Override
    public Iterator<Group> memberOf() throws RepositoryException {
        // TODO
        return null;
    }

    @Override
    public void remove() throws RepositoryException {
        // don't allow for removal of the administrator even if the executing
        // session has all permissions.
        if (!isGroup() && ((User) this).isAdmin()) {
            throw new RepositoryException("The administrator cannot be removed.");
        }
        Session s = node.getSession();
        userManager.onRemove(this);
        node.remove();
    }

    @Override
    public Iterator<String> getPropertyNames() throws RepositoryException {
        return getPropertyNames(".");
    }

    @Override
    public Iterator<String> getPropertyNames(String relPath) throws RepositoryException {
        Node n = node.getNode(relPath);
        if (Text.isDescendantOrEqual(node.getPath(), n.getPath())) {
            List<String> l = new ArrayList<String>();
            for (PropertyIterator it = n.getProperties(); it.hasNext();) {
                Property prop = it.nextProperty();
                if (isAuthorizableProperty(prop, false)) {
                    l.add(prop.getName());
                }
            }
            return l.iterator();
        } else {
            throw new IllegalArgumentException("Relative path " + relPath + " refers to items outside of scope of authorizable " + getID());
        }
    }

    @Override
    public boolean hasProperty(String relPath) throws RepositoryException {
        return node.hasProperty(relPath) && isAuthorizableProperty(node.getProperty(relPath), true);
    }

    @Override
    public Value[] getProperty(String relPath) throws RepositoryException {
        Value[] values = null;
        if (node.hasProperty(relPath)) {
            Property prop = node.getProperty(relPath);
            if (isAuthorizableProperty(prop, true)) {
                if (prop.isMultiple()) {
                    values = prop.getValues();
                } else {
                    values = new Value[]{prop.getValue()};
                }
            }
        }
        return values;
    }

    @Override
    public void setProperty(String relPath, Value value) throws RepositoryException {
        String name = Text.getName(relPath);
        String intermediate = (relPath.equals(name)) ? null : Text.getRelativeParent(relPath, 1);

        Node n = getOrCreateTargetNode(intermediate);
        // check if the property has already been created as multi valued
        // property before -> in this case remove in order to avoid
        // ValueFormatException.
        if (n.hasProperty(name)) {
            Property p = n.getProperty(name);
            if (p.isMultiple()) {
                p.remove();
            }
        }
        n.setProperty(name, value);
    }

    @Override
    public void setProperty(String relPath, Value[] values) throws RepositoryException {
        String name = Text.getName(relPath);
        String intermediate = (relPath.equals(name)) ? null : Text.getRelativeParent(relPath, 1);

        Node n = getOrCreateTargetNode(intermediate);
        // check if the property has already been created as single valued
        // property before -> in this case remove in order to avoid
        // ValueFormatException.
        if (n.hasProperty(name)) {
            Property p = n.getProperty(name);
            if (!p.isMultiple()) {
                p.remove();
            }
        }
        n.setProperty(name, values);
    }

    @Override
    public boolean removeProperty(String relPath) throws RepositoryException {
        String name = Text.getName(relPath);

        if (node.hasProperty(relPath)) {
            Property p = node.getProperty(relPath);
            if (isAuthorizableProperty(p, true)) {
                p.remove();
                return true;
            }
        }
        // no such property or wasn't a property of this authorizable.
        return false;
    }

    @Override
    public String getPath() throws RepositoryException {
        return node.getPath();
    }

    //-------------------------------------------------------------< Object >---
    @Override
    public int hashCode() {
        if (hashCode == 0) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(isGroup() ? "group:" : "user:");
                sb.append(node.getSession().getWorkspace().getName());
                sb.append(':');
                sb.append(node.getIdentifier());
                hashCode = sb.toString().hashCode();
            } catch (RepositoryException e) {
            }
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AuthorizableImpl) {
            AuthorizableImpl otherAuth = (AuthorizableImpl) obj;
            try {
                return isGroup() == otherAuth.isGroup() && node.isSame(otherAuth.node);
            } catch (RepositoryException e) {
                // should not occur -> return false in this case.
            }
        }
        return false;
    }

    @Override
    public String toString() {
        try {
            String typeStr = (isGroup()) ? "Group '" : "User '";
            return new StringBuilder().append(typeStr).append(getID()).append('\'').toString();
        } catch (RepositoryException e) {
            return super.toString();
        }
    }

    //--------------------------------------------------------------------------
    /**
     * @return node The node associated with this authorizable instance.
     */
    NodeImpl getNode() {
        return node;
    }

    UserManagerImpl getUserManager() {
        return userManager;
    }

    String getPrincipalName() throws RepositoryException {
        String principalName;
        if (node.hasProperty(REP_PRINCIPAL_NAME)) {
            principalName = node.getProperty(REP_PRINCIPAL_NAME).getString();
        } else {
            log.debug("Authorizable without principal name -> using ID as fallback.");
            principalName = getID();
        }
        return principalName;
    }

    /**
     * Returns true if the given property of the authorizable node is one of the
     * non-protected properties defined by the rep:Authorizable node type or a
     * some other descendant of the authorizable node.
     *
     * @param prop Property to be tested.
     * @param verifyAncestor If true the property is tested to be a descendant
     * of the node of this authorizable; otherwise it is expected that this
     * test has been executed by the caller.
     * @return {@code true} if the given property is defined
     * by the rep:authorizable node type or one of it's sub-node types;
     * {@code false} otherwise.
     * @throws RepositoryException If the property definition cannot be retrieved.
     */
    private boolean isAuthorizableProperty(Property prop, boolean verifyAncestor) throws RepositoryException {
        if (verifyAncestor && !Text.isDescendant(node.getPath(), prop.getPath())) {
            log.debug("Attempt to access property outside of authorizable scope.");
            return false;
        }

        PropertyDefinition def = prop.getDefinition();
        if (def.isProtected()) {
            return false;
        } else if (node.isSame(prop.getParent())) {
            NodeType declaringNt = prop.getDefinition().getDeclaringNodeType();
            return declaringNt.isNodeType(NT_REP_AUTHORIZABLE);
        } else {
            // another non-protected property somewhere in the subtree of this
            // authorizable node -> is a property that can be set using #setProperty.
            return true;
        }
    }

    /**
     *
     * @param relPath A relative path.
     * @return The corresponding node.
     * @throws RepositoryException If an error occurs.
     */
    private Node getOrCreateTargetNode(String relPath) throws RepositoryException {
        Node n;
        if (relPath != null) {
            n = JcrUtils.getOrCreateByPath(node, relPath, false, null, null, false);
            if (!Text.isDescendantOrEqual(node.getPath(), n.getPath())) {
                node.refresh(false);
                throw new RepositoryException("Relative path " + relPath + " outside of scope of " + this);
            }
        } else {
            n = node;
        }
        return n;
    }
}