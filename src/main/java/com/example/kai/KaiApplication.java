package com.example.kai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.kai.config.KaiConfig;
import com.example.kai.config.ModelCheck;

@SpringBootApplication
public class KaiApplication {

	public static void main(String[] args) {
		// 1. Settings first: a missing or wrong kai.properties stops here with a plain message
		KaiConfig.Loaded config;
		try {
			config = KaiConfig.load(args);
		}
		catch (KaiConfig.Invalid e) {
			KaiConfig.exit(e.getMessage());
			return;
		}

		// 2. Start Spring with the checked settings as the KaiProperties bean
		SpringApplication app = new SpringApplication(KaiApplication.class);
		app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("kaiProperties", config.properties()));
		ConfigurableApplicationContext ctx;
		try {
			ctx = app.run(KaiConfig.springArgs(args, config.file()));
		}
		catch (Exception e) { // e.g. port already in use; Spring has already logged the details
			KaiConfig.exit("Kai could not start. The reason is shown above.");
			return;
		}

		// 3. AI key and address: set, and answering? (one tiny model call)
		System.out.println("Checking the connection to the AI service...");
		String problem = ctx.getBean(ModelCheck.class).problem();
		if (problem != null) {
			ctx.close();
			KaiConfig.exit(problem);
			return;
		}

		// 4. Tell the user where to go
		String url = "http://localhost:" + ctx.getEnvironment().getProperty("local.server.port");
		System.out.println();
		System.out.println("Kai is running at " + url);
		System.out.println("Keep this window open while you use Kai. Close it to stop Kai.");
		System.out.println();
	}
}
