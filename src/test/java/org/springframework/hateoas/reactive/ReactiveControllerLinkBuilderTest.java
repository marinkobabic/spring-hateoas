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
package org.springframework.hateoas.reactive;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;
import static org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.*;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.IanaLinkRelation;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.ReactiveWebClientConfigurer;
import org.springframework.hateoas.mvc.TypeReferences.ResourceType;
import org.springframework.hateoas.mvc.TypeReferences.ResourcesType;
import org.springframework.hateoas.support.Employee;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Greg Turnquist
 */
public class ReactiveControllerLinkBuilderTest {

	ParameterizedTypeReference<Resource<Employee>> resourceEmployeeType = new ResourceType<Employee>() {};
	ParameterizedTypeReference<Resources<Resource<Employee>>> resourcesEmployeeType = new ResourcesType<Resource<Employee>>() {};

	WebTestClient testClient;

	void setUp(Class<?> context) {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(context);
		ctx.refresh();

		ReactiveWebClientConfigurer webClientConfigurer = ctx.getBean(ReactiveWebClientConfigurer.class);

		this.testClient = WebTestClient.bindToApplicationContext(ctx).build()
			.mutate()
			.exchangeStrategies(webClientConfigurer.hypermediaExchangeStrategies())
			.build();
	}

	@Test
	public void simpleLinkCreationShouldWork() {

		setUp(HalWebFluxConfig.class);

		this.testClient.get().uri("/")
			.accept(MediaTypes.HAL_JSON)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(MediaTypes.HAL_JSON_UTF8)
			.returnResult(ResourceSupport.class)
			.getResponseBody()
			.as(StepVerifier::create)
			.expectNextMatches(resourceSupport -> {

				assertThat(resourceSupport.getLinks()).containsExactlyInAnyOrder(
					new Link("/", IanaLinkRelation.SELF.value()),
					new Link("/employees", "employees"));

				return true;
			})
			.verifyComplete();

		this.testClient.get().uri("/employees")
			.accept(MediaTypes.HAL_JSON)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(MediaTypes.HAL_JSON_UTF8)
			.returnResult(this.resourcesEmployeeType)
			.getResponseBody()
			.as(StepVerifier::create)
			.expectNextMatches(resources -> {
				
				assertThat(resources.getContent()).extracting("content").containsExactlyInAnyOrder(
					new Employee("Frodo Baggins", "ring bearer")
				);

				assertThat(resources.getContent()).extracting("links").containsExactlyInAnyOrder(
					Arrays.asList(
						new Link("/employees/Frodo%20Baggins", IanaLinkRelation.SELF.value()),
						new Link("/employees", "employees")));

				assertThat(resources.getLinks()).containsExactlyInAnyOrder(
					new Link("/employees", IanaLinkRelation.SELF.value()),
					new Link("/", "root")
				);

				return true;
			})
			.verifyComplete();

		this.testClient.get().uri("/employees/Frodo%20Baggins")
			.accept(MediaTypes.HAL_JSON)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(MediaTypes.HAL_JSON_UTF8)
			.returnResult(this.resourceEmployeeType)
			.getResponseBody()
			.as(StepVerifier::create)
			.expectNextMatches(resource -> {

				assertThat(resource.getContent()).isEqualTo(new Employee("Frodo Baggins", "ring bearer"));
				assertThat(resource.getLinks()).containsExactlyInAnyOrder(
					new Link("/employees/Frodo%20Baggins", IanaLinkRelation.SELF.value()),
					new Link("/employees", "employees")
				);

				return true;
			})
			.verifyComplete();
	}

	@Configuration
	@EnableWebFlux
	@EnableHypermediaSupport(type = HAL)
	static class HalWebFluxConfig {

		@Bean
		TestController testController() {
			return new TestController();
		}
	}

	@RestController
	static class TestController {

		private List<Employee> employees;
		private ReactiveEmployeeResourceAssembler assembler = new ReactiveEmployeeResourceAssembler();

		TestController() {

			this.employees = new ArrayList<>();
			this.employees.add(new Employee("Frodo Baggins", "ring bearer"));
		}

		@GetMapping
		Mono<ResourceSupport> root(ServerWebExchange exchange) {

			ResourceSupport root = new ResourceSupport();


			root.add(linkTo(methodOn(TestController.class).root(null), exchange).withSelfRel());
			root.add(linkTo(methodOn(TestController.class).employees(null), exchange).withRel("employees"));

			return Mono.just(root);
		}

		@GetMapping("/employees")
		Mono<Resources<Resource<Employee>>> employees(ServerWebExchange exchange) {

			return findAll()
				.collectList()
				.map(resources -> this.assembler.toResources(resources, exchange));
		}

		@GetMapping("/employees/{id}")
		Mono<Resource<Employee>> employee(@PathVariable String id, ServerWebExchange exchange) {
			return findById("Frodo Baggins")
				.map(employee -> this.assembler.toResource(employee, exchange));
		}

		Mono<Employee> findById(String id) {

			return Mono.just(this.employees.stream()
				.filter(employee -> employee.getName().equals(id))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("No employee by the name of '" + id + "'")));
		}

		Flux<Employee> findAll() {
			return Flux.fromIterable(this.employees);
		}
	}

	static class ReactiveEmployeeResourceAssembler {

		Resource<Employee> toResource(Employee employee, ServerWebExchange exchange) {

			Resource<Employee> employeeResource = new Resource<>(employee);

			employeeResource.add(linkTo(methodOn(TestController.class).employee(employee.getName(), exchange), exchange).withSelfRel());
			employeeResource.add(linkTo(methodOn(TestController.class).employees(exchange), exchange).withRel("employees"));

			return employeeResource;
		}

		Resources<Resource<Employee>> toResources(List<Employee> employees, ServerWebExchange exchange) {

			Resources<Resource<Employee>> resources = new Resources<>(employees.stream()
				.map(employee -> this.toResource(employee, exchange))
				.collect(Collectors.toList()));

			resources.add(linkTo(methodOn(TestController.class).employees(exchange), exchange).withSelfRel());
			resources.add(linkTo(methodOn(TestController.class).root(exchange), exchange).withRel("root"));

			return resources;
		}
	}
}
