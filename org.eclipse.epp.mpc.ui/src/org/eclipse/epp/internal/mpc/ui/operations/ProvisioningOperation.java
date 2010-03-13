/*******************************************************************************
 * Copyright (c) 2009 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.ui.operations;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.equinox.internal.p2.discovery.model.CatalogItem;
import org.eclipse.equinox.internal.p2.ui.discovery.DiscoveryUi;
import org.eclipse.equinox.internal.p2.ui.discovery.util.WorkbenchUtil;
import org.eclipse.equinox.internal.p2.ui.discovery.wizards.Messages;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.operations.InstallOperation;
import org.eclipse.equinox.p2.operations.ProfileChangeOperation;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.equinox.p2.operations.UninstallOperation;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;

/**
 * A job that configures a p2 provisioning operation for installing/updating/removing one or more {@link CatalogItem
 * connectors}. The bulk of the installation work is done by p2; this class just sets up the p2 repository meta-data and
 * selects the appropriate features to install.
 * 
 * @author David Green
 * @author Steffen Pingel
 */
public class ProvisioningOperation implements IRunnableWithProgress {

	public enum OperationType {
		INSTALL, UPDATE, UNINSTALL
	}

	private static final String P2_FEATURE_GROUP_SUFFIX = ".feature.group"; //$NON-NLS-1$

	private final List<CatalogItem> installableConnectors;

	private final ProvisioningUI provisioningUI;

	private Set<URI> repositoryLocations;

	private final OperationType operationType;

	public ProvisioningOperation(OperationType operationType, List<CatalogItem> installableConnectors) {
		if (operationType == null) {
			throw new IllegalArgumentException();
		}
		if (installableConnectors == null || installableConnectors.isEmpty()) {
			throw new IllegalArgumentException();
		}
		this.operationType = operationType;
		this.installableConnectors = new ArrayList<CatalogItem>(installableConnectors);
		this.provisioningUI = ProvisioningUI.getDefaultUI();
	}

	public void run(IProgressMonitor progressMonitor) throws InvocationTargetException, InterruptedException {
		try {
			SubMonitor monitor = SubMonitor.convert(progressMonitor, Messages.InstallConnectorsJob_task_configuring,
					100);
			try {
				final IInstallableUnit[] ius = computeInstallableUnits(monitor.newChild(50));

				checkCancelled(monitor);

				switch (operationType) {
				case INSTALL: {
					final InstallOperation operation = resolveInstall(monitor.newChild(50), ius,
							repositoryLocations.toArray(new URI[0]));
					checkCancelled(monitor);
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							provisioningUI.openInstallWizard(Arrays.asList(ius), operation, null);
						}
					});

				}
					break;
				case UNINSTALL: {
					final UninstallOperation operation = resolveUninstall(monitor.newChild(50), ius,
							repositoryLocations.toArray(new URI[0]));
					checkCancelled(monitor);
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							provisioningUI.openUninstallWizard(Arrays.asList(ius), operation, null);
						}
					});

				}
					break;
				case UPDATE: {
					final UpdateOperation operation = resolveUpdate(monitor.newChild(50), ius,
							repositoryLocations.toArray(new URI[0]));
					checkCancelled(monitor);
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							provisioningUI.openUpdateWizard(true, operation, null);
						}
					});
				}
					break;
				default:
					throw new UnsupportedOperationException(operationType.name());
				}
			} finally {
				monitor.done();
			}
		} catch (OperationCanceledException e) {
			throw new InterruptedException();
		} catch (Exception e) {
			throw new InvocationTargetException(e);
		}
	}

	private void checkCancelled(IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	private InstallOperation resolveInstall(IProgressMonitor monitor, final IInstallableUnit[] ius, URI[] repositories)
			throws CoreException {
		InstallOperation operation = provisioningUI.getInstallOperation(Arrays.asList(ius), repositories);
		resolveModal(monitor, operation);
		return operation;
	}

	private UninstallOperation resolveUninstall(IProgressMonitor monitor, final IInstallableUnit[] ius,
			URI[] repositories) throws CoreException {
		UninstallOperation operation = provisioningUI.getUninstallOperation(Arrays.asList(ius), repositories);
		resolveModal(monitor, operation);
		return operation;
	}

	private UpdateOperation resolveUpdate(IProgressMonitor monitor, final IInstallableUnit[] ius, URI[] repositories)
			throws CoreException {
		UpdateOperation operation = provisioningUI.getUpdateOperation(Arrays.asList(ius), repositories);
		resolveModal(monitor, operation);
		return operation;
	}

	public void resolveModal(IProgressMonitor monitor, ProfileChangeOperation operation) throws CoreException {
		IStatus operationStatus = operation.resolveModal(new SubProgressMonitor(monitor, installableConnectors.size()));
		if (operationStatus.getSeverity() > IStatus.WARNING) {
			throw new CoreException(operationStatus);
		}
	}

	public IInstallableUnit[] computeInstallableUnits(SubMonitor monitor) throws CoreException {
		try {
			monitor.setWorkRemaining(100);
			// add repository urls and load meta data
			List<IMetadataRepository> repositories = addRepositories(monitor.newChild(50));
			final List<IInstallableUnit> installableUnits = queryInstallableUnits(monitor.newChild(50), repositories);
			removeOldVersions(installableUnits);
			checkForUnavailable(installableUnits);
			return installableUnits.toArray(new IInstallableUnit[installableUnits.size()]);

			//			MultiStatus status = new MultiStatus(DiscoveryUi.ID_PLUGIN, 0, Messages.PrepareInstallProfileJob_ok, null);
			//			ius = installableUnits.toArray(new IInstallableUnit[installableUnits.size()]);
			//			ProfileChangeRequest profileChangeRequest = InstallAction.computeProfileChangeRequest(ius, profileId,
			//					status, new SubProgressMonitor(monitor, installableConnectors.size()));
			//			if (status.getSeverity() > IStatus.WARNING) {
			//				throw new CoreException(status);
			//			}
			//			if (profileChangeRequest == null) {
			//				// failed but no indication as to why
			//				throw new CoreException(new Status(IStatus.ERROR, DiscoveryUi.ID_PLUGIN,
			//						Messages.PrepareInstallProfileJob_computeProfileChangeRequestFailed, null));
			//			}
			//			PlannerResolutionOperation operation = new PlannerResolutionOperation(
			//					Messages.PrepareInstallProfileJob_calculatingRequirements, profileId, profileChangeRequest, null,
			//					status, true);
			//			IStatus operationStatus = operation.execute(new SubProgressMonitor(monitor, installableConnectors.size()));
			//			if (operationStatus.getSeverity() > IStatus.WARNING) {
			//				throw new CoreException(operationStatus);
			//			}
			//
			//			plannerResolutionOperation = operation;

		} catch (URISyntaxException e) {
			// should never happen, since we already validated URLs.
			throw new CoreException(new Status(IStatus.ERROR, DiscoveryUi.ID_PLUGIN,
					Messages.InstallConnectorsJob_unexpectedError_url, e));
		} catch (MalformedURLException e) {
			// should never happen, since we already validated URLs.
			throw new CoreException(new Status(IStatus.ERROR, DiscoveryUi.ID_PLUGIN,
					Messages.InstallConnectorsJob_unexpectedError_url, e));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Verifies that we found what we were looking for: it's possible that we have connector descriptors that are no
	 * longer available on their respective sites. In that case we must inform the user. Unfortunately this is the
	 * earliest point at which we can know.
	 */
	private void checkForUnavailable(final List<IInstallableUnit> installableUnits) throws CoreException {
		// at least one selected connector could not be found in a repository
		Set<String> foundIds = new HashSet<String>();
		for (IInstallableUnit unit : installableUnits) {
			foundIds.add(unit.getId());
		}

		String message = ""; //$NON-NLS-1$
		String detailedMessage = ""; //$NON-NLS-1$
		for (CatalogItem descriptor : installableConnectors) {
			StringBuilder unavailableIds = null;
			for (String id : getFeatureIds(descriptor)) {
				if (!foundIds.contains(id)) {
					if (unavailableIds == null) {
						unavailableIds = new StringBuilder();
					} else {
						unavailableIds.append(Messages.InstallConnectorsJob_commaSeparator);
					}
					unavailableIds.append(id);
				}
			}
			if (unavailableIds != null) {
				if (message.length() > 0) {
					message += Messages.InstallConnectorsJob_commaSeparator;
				}
				message += descriptor.getName();

				if (detailedMessage.length() > 0) {
					detailedMessage += Messages.InstallConnectorsJob_commaSeparator;
				}
				detailedMessage += NLS.bind(Messages.PrepareInstallProfileJob_notFoundDescriptorDetail, new Object[] {
						descriptor.getName(), unavailableIds.toString(), descriptor.getSiteUrl() });
			}
		}

		if (message.length() > 0) {
			// instead of aborting here we ask the user if they wish to proceed anyways
			final boolean[] okayToProceed = new boolean[1];
			final String finalMessage = message;
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					okayToProceed[0] = MessageDialog.openQuestion(WorkbenchUtil.getShell(),
							Messages.InstallConnectorsJob_questionProceed, NLS.bind(
									Messages.InstallConnectorsJob_questionProceed_long, new Object[] { finalMessage }));
				}
			});
			if (!okayToProceed[0]) {
				throw new CoreException(new Status(IStatus.ERROR, DiscoveryUi.ID_PLUGIN, NLS.bind(
						Messages.InstallConnectorsJob_connectorsNotAvailable, detailedMessage), null));
			}
		}
	}

	/**
	 * Filters those installable units that have a duplicate in the list with a higher version number. it's possible
	 * that some repositories will host multiple versions of a particular feature. we assume that the user wants the
	 * highest version.
	 */
	private void removeOldVersions(final List<IInstallableUnit> installableUnits) {
		Map<String, Version> symbolicNameToVersion = new HashMap<String, Version>();
		for (IInstallableUnit unit : installableUnits) {
			Version version = symbolicNameToVersion.get(unit.getId());
			if (version == null || version.compareTo(unit.getVersion()) == -1) {
				symbolicNameToVersion.put(unit.getId(), unit.getVersion());
			}
		}
		if (symbolicNameToVersion.size() != installableUnits.size()) {
			for (IInstallableUnit unit : new ArrayList<IInstallableUnit>(installableUnits)) {
				Version version = symbolicNameToVersion.get(unit.getId());
				if (!version.equals(unit.getVersion())) {
					installableUnits.remove(unit);
				}
			}
		}
	}

	/**
	 * Perform a query to get the installable units. This causes p2 to determine what features are available in each
	 * repository. We select installable units by matching both the feature id and the repository; it is possible though
	 * unlikely that the same feature id is available from more than one of the selected repositories, and we must
	 * ensure that the user gets the one that they asked for.
	 */
	private List<IInstallableUnit> queryInstallableUnits(SubMonitor monitor, List<IMetadataRepository> repositories)
			throws URISyntaxException {
		final List<IInstallableUnit> installableUnits = new ArrayList<IInstallableUnit>();

		monitor.setWorkRemaining(repositories.size());
		for (final IMetadataRepository repository : repositories) {
			checkCancelled(monitor);
			final Set<String> installableUnitIdsThisRepository = getDescriptorIds(repository);
			IQuery<IInstallableUnit> query = QueryUtil.createMatchQuery( //
			"id ~= /*.feature.group/ && " + //$NON-NLS-1$
					"properties['org.eclipse.equinox.p2.type.group'] == true ");//$NON-NLS-1$
			IQueryResult<IInstallableUnit> result = repository.query(query, monitor.newChild(1));
			for (Iterator<IInstallableUnit> iter = result.iterator(); iter.hasNext();) {
				IInstallableUnit iu = iter.next();
				String id = iu.getId();
				if (installableUnitIdsThisRepository.contains(id)) {
					installableUnits.add(iu);
				}
			}
		}
		return installableUnits;
	}

	private List<IMetadataRepository> addRepositories(SubMonitor monitor) throws MalformedURLException,
			URISyntaxException, ProvisionException {
		// tell p2 that it's okay to use these repositories
		ProvisioningSession session = ProvisioningUI.getDefaultUI().getSession();
		RepositoryTracker repositoryTracker = ProvisioningUI.getDefaultUI().getRepositoryTracker();
		repositoryLocations = new HashSet<URI>();
		monitor.setWorkRemaining(installableConnectors.size() * 5);
		for (CatalogItem descriptor : installableConnectors) {
			URI uri = new URL(descriptor.getSiteUrl()).toURI();
			if (repositoryLocations.add(uri)) {
				checkCancelled(monitor);
				repositoryTracker.addRepository(uri, null, session);
				//					ProvisioningUtil.addMetaDataRepository(url.toURI(), true);
				//					ProvisioningUtil.addArtifactRepository(url.toURI(), true);
				//					ProvisioningUtil.setColocatedRepositoryEnablement(url.toURI(), true);
			}
			monitor.worked(1);
		}

		// fetch meta-data for these repositories
		ArrayList<IMetadataRepository> repositories = new ArrayList<IMetadataRepository>();
		monitor.setWorkRemaining(repositories.size());
		IMetadataRepositoryManager manager = (IMetadataRepositoryManager) session.getProvisioningAgent().getService(
				IMetadataRepositoryManager.SERVICE_NAME);
		for (URI uri : repositoryLocations) {
			checkCancelled(monitor);
			IMetadataRepository repository = manager.loadRepository(uri, monitor.newChild(1));
			repositories.add(repository);
		}
		return repositories;
	}

	private Set<String> getDescriptorIds(final IMetadataRepository repository) throws URISyntaxException {
		final Set<String> installableUnitIdsThisRepository = new HashSet<String>();
		// determine all installable units for this repository
		for (CatalogItem descriptor : installableConnectors) {
			try {
				if (repository.getLocation().equals(new URL(descriptor.getSiteUrl()).toURI())) {
					installableUnitIdsThisRepository.addAll(getFeatureIds(descriptor));
				}
			} catch (MalformedURLException e) {
				// will never happen, ignore
			}
		}
		return installableUnitIdsThisRepository;
	}

	private Set<String> getFeatureIds(CatalogItem descriptor) {
		Set<String> featureIds = new HashSet<String>();
		for (String id : descriptor.getInstallableUnits()) {
			if (!id.endsWith(P2_FEATURE_GROUP_SUFFIX)) {
				id += P2_FEATURE_GROUP_SUFFIX;
			}
			featureIds.add(id);
		}
		return featureIds;
	}

}