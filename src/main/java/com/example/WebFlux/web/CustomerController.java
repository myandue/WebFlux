package com.example.WebFlux.web;

import com.example.WebFlux.domain.Customer;
import com.example.WebFlux.domain.CustomerRepository;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@RestController
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final Sinks.Many<Customer> sink;

    //A 요청 -> Flux -> Stream
    //B 요청 -> Flux -> Stream
    //sink: 위의 각기 다른 Flux를 하나로 합쳐준다. (Flux.merge)
    //즉, 모든 Client가 해당 Flux에 접근할 수 있다.

    public CustomerController(CustomerRepository customerRepository){
        this.customerRepository=customerRepository;
        //multicast: 새로 push된 데이터만 받는다.
        sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @GetMapping("/flux")
    public Flux<Integer> flux(){
        //just: onNext로 데이터들을 순차적으로 던져줌
        //delayElements: 해당 데이터들이 다 불러진 후에야(onComplete까지 도달)
        //브라우저에 뜨는 것을 확인하기 위한 의도적 딜레이
        return Flux.just(1,2,3,4,5).delayElements(Duration.ofSeconds(1)).log();
    }

    @GetMapping(value="/fluxstream", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public Flux<Integer> fluxstream(){
        //위의 메서드와 다른점은,
        //위의 메서드는 onComplete까지 도달해야 응답을 하지만
        //해당 메서드는 onNext마다 응답을 한다. (응답 유지)
        //똑같은디?
        return Flux.just(1,2,3,4,5).delayElements(Duration.ofSeconds(1)).log();
    }

    @GetMapping(value="/customer", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public Flux<Customer> findAll(){
        //ReactiveCrudRepository 에 findAll 메서드 있음
        //onComplete()이 수행된 후에 응답됨(브라우저 상에 뜸)
        return customerRepository.findAll().delayElements(Duration.ofSeconds(1)).log();
    }

    @GetMapping("/customer/{id}")
    public Mono<Customer> findById(@PathVariable Long id){
        //findById는 값을 하나 받기때문에 Mono로 받는다.
        //onNext 한번에 응답이 끝난다.
        return customerRepository.findById(id).log();
    }

    //위의 메서드들(web flux? reactive stream?)은 준비된 데이터를 모두 던져주면 응답 끝
    //sse는 계속 응답을 유지하는 것이다!
    //하기의 produces가 sse의 응답 표준(js에서 이벤트로 데이터를 받을 수 있다) -> sse 프로토콜이 적용돼서 응답됨
    //하기와 같이 return시 produces 생략 가능(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @GetMapping("/customer/sse")
    public Flux<ServerSentEvent<Customer>> findAllSSE(){
        //합쳐진 데이터 응답
        return sink.asFlux().map(c-> ServerSentEvent.builder(c).build()).doOnCancel(()->{

            //강종시켰을 때 마지막 데이터라고 알려주기 위함
            //강종시켰다가 다시 해당 url 접근할 때 응답 유지가 안돼서 해주는건데,,
            //이런거 없어도 그냥 잘 실행되던데 잘 모루겠슴,,
            sink.asFlux().blockLast();
        });
    }

    @PostMapping("/customer")
    public Mono<Customer> save(){
        //doOnNext: save 한 후에, sink에 push 해주어야함.
        //저장한 customer c를 emit하여 데이터를 추가시킴.
        return customerRepository.save(new Customer("Gildong","Hong")).doOnNext(c->{
            sink.tryEmitNext(c);
        });
    }
}
