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

import lombok.RequiredArgsConstructor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.hateoas.support.WebStackUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link BeanPostProcessor} to register the proper handlers in any instance of {@link WebClient} found
 * in the {@link org.springframework.context.ApplicationContext}.
 * 
 * @author Greg Turnquist
 */
@RequiredArgsConstructor
public class ReactiveWebClientBeanPostProcessor implements BeanPostProcessor {

	private final ObjectFactory<ReactiveWebClientConfigurer> webClientConfigurerFactory;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

		if (WebStackUtils.REACTOR.isAvailable()) {
			if (bean instanceof WebClient) {

				ReactiveWebClientConfigurer reactiveWebClientConfigurer = this.webClientConfigurerFactory.getObject();
				return reactiveWebClientConfigurer.registerHypermediaTypes((WebClient) bean);
			}
		}

		return bean;
	}

}
