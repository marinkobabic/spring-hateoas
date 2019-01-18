/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.hateoas.config;

import static org.springframework.hateoas.config.HypermediaObjectMapperCreator.*;

import lombok.RequiredArgsConstructor;

import java.util.Collection;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RelProvider;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.core.DelegatingRelProvider;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.hal.HalConfiguration;
import org.springframework.hateoas.hal.forms.HalFormsConfiguration;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link WebFluxConfigurer} to register hypermedia-aware encoders and decoders.
 * 
 * @author Greg Turnquist
 */
@Configuration
@RequiredArgsConstructor
public class HypermediaEncoderDecoderWebFluxConfigurer implements WebFluxConfigurer, BeanFactoryAware {

	private static final String MESSAGE_SOURCE_BEAN_NAME = "linkRelationMessageSource";

	private final ObjectProvider<ObjectMapper> mapper;
	private final ObjectProvider<DelegatingRelProvider> relProvider;
	private final ObjectProvider<CurieProvider> curieProvider;
	private final ObjectProvider<HalConfiguration> halConfiguration;
	private final ObjectProvider<HalFormsConfiguration> halFormsConfiguration;

	private BeanFactory beanFactory;
	private Collection<HypermediaType> hypermediaTypes;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * @param hyperMediaTypes the hyperMediaTypes to set
	 */
	public void setHypermediaTypes(Collection<HypermediaType> hyperMediaTypes) {
		this.hypermediaTypes = hyperMediaTypes;
	}

	/**
	 * Configure custom HTTP message readers and writers or override built-in ones.
	 * <p>The configured readers and writers will be used for both annotated
	 * controllers and functional endpoints.
	 *
	 * @param configurer the configurer to use
	 */
	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

		if (configurer.getReaders().stream()
			.flatMap(httpMessageReader -> httpMessageReader.getReadableMediaTypes().stream())
			.anyMatch(mediaType -> mediaType == MediaTypes.HAL_JSON
				|| mediaType == MediaTypes.HAL_JSON_UTF8
				|| mediaType == MediaTypes.HAL_FORMS_JSON
				|| mediaType == MediaTypes.COLLECTION_JSON
				|| mediaType == MediaTypes.UBER_JSON)) {

			// Already configured for hypermedia!
			return;
		}

		ObjectMapper objectMapper = mapper.getIfAvailable(ObjectMapper::new);

		MessageSourceAccessor linkRelationMessageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME,
			MessageSourceAccessor.class);

		CodecConfigurer.CustomCodecs customCodecs = configurer.customCodecs();

		if (hypermediaTypes.stream().anyMatch(HypermediaType::isHalBasedMediaType)) {

			CurieProvider curieProvider = this.curieProvider.getIfAvailable();
			RelProvider relProvider = this.relProvider.getObject();

			if (hypermediaTypes.contains(HypermediaType.HAL)) {

				ObjectMapper halObjectMapper = createHalObjectMapper(objectMapper, curieProvider, relProvider,
					linkRelationMessageSource, this.halConfiguration);

				customCodecs.decoder(
					new Jackson2JsonDecoder(halObjectMapper, MediaTypes.HAL_JSON, MediaTypes.HAL_JSON_UTF8));
				customCodecs.encoder(
					new Jackson2JsonEncoder(halObjectMapper, MediaTypes.HAL_JSON, MediaTypes.HAL_JSON_UTF8));
			}

			if (hypermediaTypes.contains(HypermediaType.HAL_FORMS)) {

				ObjectMapper halFormsObjectMapper = createHalFormsObjectMapper(objectMapper, curieProvider,
					relProvider, linkRelationMessageSource, this.halFormsConfiguration);

				customCodecs.decoder(
					new Jackson2JsonDecoder(halFormsObjectMapper, MediaTypes.HAL_FORMS_JSON));
				customCodecs.encoder(
					new Jackson2JsonEncoder(halFormsObjectMapper, MediaTypes.HAL_FORMS_JSON));
			}
		}

		if (hypermediaTypes.contains(HypermediaType.COLLECTION_JSON)) {

			ObjectMapper collectionJsonObjectMapper = createCollectionJsonObjectMapper(objectMapper, linkRelationMessageSource);

			customCodecs.decoder(
				new Jackson2JsonDecoder(collectionJsonObjectMapper, MediaTypes.COLLECTION_JSON));
			customCodecs.encoder(
				new Jackson2JsonEncoder(collectionJsonObjectMapper, MediaTypes.COLLECTION_JSON));
		}

		if (hypermediaTypes.contains(HypermediaType.UBER)) {

			ObjectMapper uberObjectMapper = createUberObjectMapper(objectMapper);

			customCodecs.decoder(
				new Jackson2JsonDecoder(uberObjectMapper, MediaTypes.UBER_JSON));
			customCodecs.encoder(
				new Jackson2JsonEncoder(uberObjectMapper, MediaTypes.UBER_JSON));
		}

		configurer.registerDefaults(false);
	}
}
