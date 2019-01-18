package org.springframework.hateoas.config;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * @author Greg Turnquist
 */
public class ConverterRegisteringBeanPostProcessorTest {

	@Test
	public void shouldRegisterJustHal() {

		withContext(HalConfig.class, context -> {

			assertThat(lookupSupportedHypermediaTypes(context.getBean(RestTemplate.class)))
				.containsExactlyInAnyOrder(
					MediaTypes.HAL_JSON,
					MediaTypes.HAL_JSON_UTF8,
					MediaType.APPLICATION_JSON,
					MediaType.parseMediaType("application/*+json"));
		});
	}

	@Test
	public void shouldRegisterHalAndCollectionJsonMessageConverters() {

		withContext(HalAndCollectionJsonConfig.class, context -> {

			assertThat(lookupSupportedHypermediaTypes(context.getBean(RestTemplate.class)))
				.containsExactlyInAnyOrder(
					MediaTypes.HAL_JSON,
					MediaTypes.HAL_JSON_UTF8,
					MediaTypes.COLLECTION_JSON,
					MediaType.APPLICATION_JSON,
					MediaType.parseMediaType("application/*+json"));
		});
	}

	@Test
	public void shouldRegisterHypermediaMessageConverters() {

		withContext(AllHypermediaConfig.class, context -> {

			assertThat(lookupSupportedHypermediaTypes(context.getBean(RestTemplate.class)))
				.containsExactlyInAnyOrder(
					MediaTypes.HAL_JSON,
					MediaTypes.HAL_JSON_UTF8,
					MediaTypes.HAL_FORMS_JSON,
					MediaTypes.COLLECTION_JSON,
					MediaTypes.UBER_JSON,
					MediaType.APPLICATION_JSON,
					MediaType.parseMediaType("application/*+json"));
		});
	}

	private List<MediaType> lookupSupportedHypermediaTypes(RestTemplate restTemplate) {
		
		return restTemplate.getMessageConverters().stream()
			.filter(MappingJackson2HttpMessageConverter.class::isInstance)
			.map(AbstractJackson2HttpMessageConverter.class::cast)
			.map(AbstractHttpMessageConverter::getSupportedMediaTypes)
			.flatMap(Collection::stream)
			.collect(Collectors.toList());
	}

	static class BaseConfig {

		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		HypermediaConverterRegisteringBeanPostProcessor springMvcConverterRegisteringBeanPostProcessor(
			ObjectFactory<HypermediaConverterRegisteringWebMvcConfigurer> springMvcConverterRegisteringWebMvcConfigurerObjectFactory) {

			return new HypermediaConverterRegisteringBeanPostProcessor(springMvcConverterRegisteringWebMvcConfigurerObjectFactory);
		}
	}

	@Configuration
	@EnableHypermediaSupport(type = HypermediaType.HAL)
	static class HalConfig extends BaseConfig {
	}

	@Configuration
	@EnableHypermediaSupport(type = {HypermediaType.HAL, HypermediaType.COLLECTION_JSON})
	static class HalAndCollectionJsonConfig extends BaseConfig {
	}

	@Configuration
	@EnableHypermediaSupport(type = {HypermediaType.HAL, HypermediaType.HAL_FORMS, HypermediaType.COLLECTION_JSON, HypermediaType.UBER})
	static class AllHypermediaConfig extends BaseConfig {
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