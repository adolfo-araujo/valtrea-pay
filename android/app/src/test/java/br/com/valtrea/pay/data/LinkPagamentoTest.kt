package br.com.valtrea.pay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class LinkPagamentoTest {

    private val id = "3f2c1b7e-8a3d-4c55-9b1e-2d6f0a9c4e11"

    @Test
    fun idaEVoltaComValor() {
        val link = LinkPagamento(id, BigDecimal("12.5"))
        assertEquals("valtrea://pagar/$id?valor=12.50", link.paraTexto())
        assertEquals(LinkPagamento(id, BigDecimal("12.50")), LinkPagamento.ler(link.paraTexto()))
    }

    @Test
    fun valorEOpcional() {
        assertEquals(LinkPagamento(id, null), LinkPagamento.ler("valtrea://pagar/$id"))
    }

    @Test
    fun valorInvalidoEIgnorado() {
        assertNull(LinkPagamento.ler("valtrea://pagar/$id?valor=-3")!!.valor)
        assertNull(LinkPagamento.ler("valtrea://pagar/$id?valor=1.234")!!.valor)
    }

    @Test
    fun recusaOutrosCodigos() {
        assertNull(LinkPagamento.ler("https://exemplo.com/pagar/$id"))
        assertNull(LinkPagamento.ler("valtrea://outra/$id"))
        assertNull(LinkPagamento.ler("valtrea://pagar/nao-e-um-id"))
        assertNull(LinkPagamento.ler("00020126580014br.gov.bcb.pix")) // QR Code Pix
    }
}
