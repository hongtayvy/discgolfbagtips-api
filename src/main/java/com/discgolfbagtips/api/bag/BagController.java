package com.discgolfbagtips.api.bag;

import com.discgolfbagtips.api.common.NotFoundException;
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

/** Physical bags — capacity, weight and dimensions — as distinct from the discs inside them. */
@RestController
@RequestMapping("/api/v1/bags")
@Validated
@Tag(name = "Bags", description = "Bag models, capacity and fitting")
public class BagController {

    private final BagFittingService bagFittingService;

    public BagController(BagFittingService bagFittingService) {
        this.bagFittingService = bagFittingService;
    }

    @GetMapping
    @Operation(summary = "List bag models, optionally only those that fit a given number of discs",
            description = "Specs are hand-researched from manufacturer listings. Where a maker does "
                    + "not publish a figure the field is null rather than estimated — `source` says "
                    + "where each row came from.")
    public List<BagSummary> list(
            @RequestParam(name = "capacity", required = false) @Min(1) @Max(60) Integer capacity,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "type", required = false) String bagType) {

        if (capacity == null) {
            return bagFittingService.catalog();
        }
        return bagFittingService.fitting(capacity, 0, brand, bagType, null).stream()
                .map(BagFit::bag).toList();
    }

    @GetMapping("/brands")
    @Operation(summary = "Bag manufacturers in the catalog")
    public List<String> brands() {
        return bagFittingService.brands();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One bag model")
    public BagSummary byId(@PathVariable("id") Long id) {
        return bagFittingService.byId(id).map(BagSummary::from)
                .orElseThrow(() -> new NotFoundException("No bag model with id " + id));
    }
}
