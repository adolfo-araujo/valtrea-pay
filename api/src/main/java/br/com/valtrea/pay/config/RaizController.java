package br.com.valtrea.pay.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Hidden;

/** Páginas abertas no navegador: documentação, termos, política de privacidade, acessibilidade e exclusão de conta. */
@Controller
@Hidden
public class RaizController {

	/** Quem abre o endereço da API no navegador cai na documentação, em vez de um 401. */
	@GetMapping("/")
	public String raiz() {
		return "redirect:/swagger-ui.html";
	}

	/** Link pedido pelo Google Play e mostrado no app. */
	@GetMapping("/privacidade")
	public String privacidade() {
		return "forward:/privacidade.html";
	}

	/** Termos de uso, mostrados no cadastro e no perfil do app. */
	@GetMapping("/termos")
	public String termos() {
		return "forward:/termos.html";
	}

	/** Declaração de acessibilidade (Lei Brasileira de Inclusão, art. 63). */
	@GetMapping("/acessibilidade")
	public String acessibilidade() {
		return "forward:/acessibilidade.html";
	}

	/** Link de exclusão de conta pedido pelo Google Play (para quem não tem mais o app). */
	@GetMapping("/excluir-conta")
	public String excluirConta() {
		return "forward:/excluir-conta.html";
	}

}
