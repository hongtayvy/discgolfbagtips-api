package com.discgolfbagtips.api.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Backs the front end's type-ahead disc picker and plastic dropdown. */
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Catalog", description = "Disc and plastic reference data synced from the DiscIt API")
public class CatalogController {

    private final DiscCatalogService discCatalogService;
    private final PlasticResolver plasticResolver;
    private final com.discgolfbagtips.api.retrieval.DiscVectorStore vectorStore;

    public CatalogController(DiscCatalogService discCatalogService, PlasticResolver plasticResolver,
            com.discgolfbagtips.api.retrieval.DiscVectorStore vectorStore) {
        this.discCatalogService = discCatalogService;
        this.plasticResolver = plasticResolver;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/discs/search")
    @Operation(summary = "Type-ahead search across mold and brand names")
    public List<DiscSummary> search(
            @RequestParam("q") String query,
            @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(50) int limit) {
        return discCatalogService.search(query, limit);
    }

    @GetMapping("/discs/{id}")
    @Operation(summary = "Fetch one disc by catalog id")
    public DiscSummary byId(@PathVariable("id") String id) {
        return discCatalogService.byId(id);
    }

    @GetMapping("/brands")
    @Operation(summary = "Manufacturers present in the catalog",
            description = "For populating a brand filter. The names returned here are exactly what "
                    + "the `filters.brands` field on a recommendation or lineup request accepts.")
    public List<String> brands() {
        return vectorStore.activeBrands();
    }

    @GetMapping("/plastics")
    @Operation(summary = "List plastic blends, optionally narrowed to one manufacturer")
    public List<PlasticSummary> plastics(@RequestParam(name = "brand", required = false) String brand) {
        List<PlasticType> plastics = brand == null || brand.isBlank()
                ? plasticResolver.all()
                : plasticResolver.forBrand(brand);
        return plastics.stream().map(PlasticSummary::from).toList();
    }
}
