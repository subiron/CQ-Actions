package com.cognifide.actions.core.internal;

import javax.jcr.Session;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.EventUtil;
import org.apache.sling.event.jobs.JobProcessor;
import org.apache.sling.event.jobs.JobUtil;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cognifide.actions.api.Action;
import com.cognifide.actions.api.ActionRegistry;
import com.cognifide.actions.core.util.AdminJcrCommandExecutor;
import com.cognifide.actions.core.util.JcrCommand;
import com.cognifide.actions.core.util.Utils;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

//@formatter:off
@Component (immediate = true)
@Service
@Properties({
	@Property(name = Constants.SERVICE_DESCRIPTION, value = "Action handle event listener."),
	@Property(name = Constants.SERVICE_VENDOR, value = "Cognifide"),
	@Property(name = "process.label", value = "[Cognifide] Action Handling"),
	@Property(name = ActionHandleEventListener.WORKING_PATH_NAME, value = ActionHandleEventListener.WORKING_PATH_DEFAULT),
	@Property(name = EventConstants.EVENT_TOPIC, value = SlingConstants.TOPIC_RESOURCE_ADDED)
})
// @formatter:on
public class ActionHandleEventListener implements EventHandler, JobProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(ActionHandleEventListener.class);

	final static String WORKING_PATH_NAME = "working.path";

	final static String WORKING_PATH_DEFAULT = "/content/usergenerated";

	@Reference
	private AdminJcrCommandExecutor executor;

	@Reference
	private ActionRegistry actionRegistry;

	@Reference
	private SlingSettingsService slingSettings;

	private String workingPath;

	@Activate
	void activate(ComponentContext ctx) throws Exception {
		workingPath = Utils.propertyToString(ctx, WORKING_PATH_NAME, WORKING_PATH_DEFAULT);
	}

	@Override
	public boolean process(Event job) {
		final String path = (String) job.getProperty(SlingConstants.PROPERTY_PATH);
		try {
			if (path.startsWith(workingPath)) {
				executor.execute(new JcrCommand() {
					@Override
					public void run(Session session, ResourceResolver resolver) throws Exception {
						PageManager pm = resolver.adaptTo(PageManager.class);
						Page page = pm.getPage(path);
						String actionType = null;
						if (page != null && page.getContentResource() != null) {
							actionType = page.getContentResource().getResourceType();
						}

						LOG.info("Incoming action: " + actionType);
						Action action = actionRegistry.getAction(actionType);
						if (action != null) {
							LOG.info("Performing action: " + actionType);
							try {
								action.perform(page);
							} catch (Exception e) {
								LOG.error("Exception occured during action " + actionType, e);
							}
							LOG.info("Action " + actionType + " finished");
						} else {
							LOG.info("No action found for: " + actionType);
						}
					}
				});

			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return true;
	}

	@Override
	public void handleEvent(Event event) {
		if (!isAuthor()) {
			return;
		}
		if (EventUtil.isLocal(event)) {
			JobUtil.processJob(event, this);
		}
	}

	private boolean isAuthor() {
		return slingSettings.getRunModes().contains("author");
	}
}