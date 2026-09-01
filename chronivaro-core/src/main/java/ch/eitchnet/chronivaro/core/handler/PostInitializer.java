package ch.eitchnet.chronivaro.core.handler;

import ch.eitchnet.chronivaro.core.jobs.GenerateSampleDataJob;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.impl.SimplePostInitializer;
import li.strolch.job.StrolchJobsHandler;

public class PostInitializer extends SimplePostInitializer {

	public PostInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	public void start() throws Exception {

		StrolchJobsHandler jobsHandler = getComponent(StrolchJobsHandler.class);
		String jobName = GenerateSampleDataJob.class.getSimpleName();
		if (jobsHandler.hasJob(jobName)) {
			runAsAgent(ctx -> {
				String source = PostInitializer.class.getSimpleName();
				jobsHandler.getJob(ctx.getCertificate(), source, jobName).runNow();
			});
		}

		super.start();
	}
}
