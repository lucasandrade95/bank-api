package com.lucasandrade.bankapi.shared;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope paginado padrao para colecoes da API.
 *
 * <p>Em vez de devolver um array cru (e potencialmente enorme), os endpoints de
 * listagem expoem o conteudo da pagina junto dos metadados de paginacao, para o
 * cliente saber se ha mais paginas a buscar.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
