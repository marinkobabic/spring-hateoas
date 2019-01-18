package org.springframework.hateoas.config;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.test.StepVerifier;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.client.Actor;
import org.springframework.hateoas.client.Movie;
import org.springframework.hateoas.client.Server;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.mvc.TypeReferences.ResourceType;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Greg Turnquist
 */
public class ReactiveWebClientBeanPostProcessorTest {

	private URI baseUri;
	private Server server;

	@Before
	public void setUp() {

		this.server = new Server();
		
		Resource<Actor> actor = new Resource<>(new Actor("Keanu Reaves"));
		String actorUri = this.server.mockResourceFor(actor);

		Movie movie = new Movie("The Matrix");
		Resource<Movie> resource = new Resource<>(movie);
		resource.add(new Link(actorUri, "actor"));

		this.server.mockResourceFor(resource);
		this.server.finishMocking();

		this.baseUri = URI.create(this.server.rootResource());
	}

	@After
	public void tearDown() throws IOException {

		if (this.server != null) {
			this.server.close();
		}
	}

	@Test
	public void shouldHandleRootHalDocument() {

		withContext(HalConfig.class, context -> {

			WebClient webClient = context.getBean(WebClient.class);

			webClient
				.get().uri(this.baseUri)
				.accept(MediaTypes.HAL_JSON)
				.retrieve()
				.bodyToMono(ResourceSupport.class)
				.as(StepVerifier::create)
				.expectNextMatches(root -> {
					assertThat(root.getLinks()).hasSize(2);
					return true;

				})
				.verifyComplete();
		});
	}

	@Test
	public void shouldHandleNavigatingToAResourceObject() {

		ParameterizedTypeReference<Resource<Actor>> typeReference = new ResourceType<Actor>() {};

		withContext(HalConfig.class, context -> {

			WebClient webClient = context.getBean(WebClient.class);

			webClient
				.get().uri(this.baseUri)
				.retrieve()
				.bodyToMono(ResourceSupport.class)
				.map(resourceSupport -> resourceSupport.getLink("actors").orElseThrow(() -> new RuntimeException("Link not found!")))
				.flatMap(link -> webClient
					.get().uri(link.expand().getHref())
					.retrieve()
					.bodyToMono(ResourceSupport.class))
				.map(resourceSupport -> resourceSupport.getLinks().get(0))
				.flatMap(link -> webClient
					.get().uri(link.expand().getHref())
					.retrieve()
					.bodyToMono(typeReference))
				.as(StepVerifier::create)
				.expectNext(new Resource<>(new Actor("Keanu Reaves")))
				.verifyComplete();
		});
	}

	@Configuration
	@EnableHypermediaSupport(type = HypermediaType.HAL)
	static class HalConfig {

		@Bean
		WebClient webClient() {
			return WebClient.create();
		}

		@Bean
		ReactiveWebClientBeanPostProcessor webClientBeanPostProcessor(
			ObjectFactory<ReactiveWebClientConfigurer> webClientConfigurerObjectFactory) {
			
			return new ReactiveWebClientBeanPostProcessor(webClientConfigurerObjectFactory);
		}
	}

	private static <E extends Exception> void withContext(Class<?> configuration,
		  ConsumerWithException<AnnotationConfigWebApplicationContext, E> consumer) throws E {

		try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {

			context.register(configuration);
			context.refresh();

			consumer.accept(context);
		}
	}

	interface ConsumerWithException<T, E extends Exception> {

		void accept(T element) throws E;
	}
}