package br.com.valtrea.pay.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Hidden;

/** Quem abre o endereço da API no navegador cai na documentação, em vez de um 401. */
@Controller
@Hidden
public class RaizController {

	@GetMapping("/")
	public String raiz() {
		return "redirect:/swagger-ui.html";
	}

}
