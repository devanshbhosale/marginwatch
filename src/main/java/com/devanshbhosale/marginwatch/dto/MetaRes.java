package com.devanshbhosale.marginwatch.dto;

import java.util.List;

/** Filter dropdown payloads for the products screen. */
public record MetaRes(List<String> categories, List<String> vendors) {
}
