/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *      The Eclipse Foundation  - initial API and implementation
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.core.service;


/**
 * @author David Green
 */
public class Category extends Identifiable {
	
	protected Integer count;
	protected java.util.List<Node> node = new java.util.ArrayList<Node>();
	
	public Category() {
	}
	
	public Integer getCount() {
		return count;
	}
	
	public void setCount(Integer count) {
		this.count = count;
	}
	
	public java.util.List<Node> getNode() {
		return node;
	}
	
	public void setNode(java.util.List<Node> node) {
		this.node = node;
	}
	
}
