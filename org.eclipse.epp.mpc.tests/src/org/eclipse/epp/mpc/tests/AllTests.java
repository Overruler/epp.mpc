/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	The Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.epp.mpc.tests;

import org.eclipse.epp.mpc.tests.service.DefaultMarketplaceServiceTest;
import org.eclipse.epp.mpc.tests.service.xml.UnmarshallerTest;
import org.eclipse.epp.mpc.tests.ui.wizard.MarketplaceUrlHandlerTest;
import org.eclipse.epp.mpc.tests.ui.wizard.SelectionModelStateSerializerTest;
import org.eclipse.epp.mpc.tests.util.TextUtilTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author David Green
 */
@RunWith(Suite.class)
@SuiteClasses({ //
	UnmarshallerTest.class, DefaultMarketplaceServiceTest.class, TextUtilTest.class,
		SelectionModelStateSerializerTest.class, MarketplaceUrlHandlerTest.class })
	public class AllTests {

}
