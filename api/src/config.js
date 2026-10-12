// What the app offers in its preferences screens. Served with the catalogue so the
// choices can change without an app update.

export const preferenceOptions = {
  allergens: [
    "Peanuts", "Tree nuts", "Milk", "Gluten", "Eggs", "Soy", "Fish", "Shellfish",
    "Sesame", "Celery", "Mustard", "Lupin", "Sulphites", "Molluscs",
  ],
  diets: [
    { id: "vegan", label: "Vegan", icon: "eco", strictness: "animal" },
    { id: "vegetarian", label: "Vegetarian", icon: "egg_alt", strictness: "animal" },
    { id: "pescatarian", label: "Pescatarian", icon: "set_meal", strictness: "animal" },
    { id: "halal", label: "Halal", icon: "mosque", strictness: "animal" },
    { id: "kosher", label: "Kosher", icon: "synagogue", strictness: "animal" },
    { id: "none", label: "No specific diet", icon: "restaurant", strictness: "none" },
  ],
  groups: [
    { id: "artificial_colours", label: "Artificial colours", icon: "palette", reason: "a colour you always avoid", shortReason: "a colour you avoid" },
    { id: "artificial_sweeteners", label: "Artificial sweeteners", icon: "water_drop", reason: "a sweetener you flagged", shortReason: "a sweetener you flagged" },
    { id: "preservatives", label: "Preservatives", icon: "inventory_2", reason: "a preservative you flagged", shortReason: "a preservative you flagged" },
    { id: "flavour_enhancers", label: "Flavour enhancers", icon: "local_fire_department", reason: "a flavour enhancer you flagged", shortReason: "a flavour enhancer you flagged" },
    { id: "palm_oil", label: "Palm oil", icon: "forest", reason: "made with palm oil, which you flagged", shortReason: "made with palm oil" },
  ],
  sensitivities: [
    { id: "children", label: "Children", description: "Flags additives linked to hyperactivity or restricted for infants", icon: "child_care", reason: "stricter for children" },
    { id: "pregnancy", label: "Pregnant or breastfeeding", description: "Flags additives with pregnancy guidance", icon: "pregnant_woman", reason: "stricter during pregnancy" },
    { id: "asthma", label: "Asthma or aspirin-sensitive", description: "Flags sulphites, benzoates and azo dyes", icon: "pulmonology", reason: "stricter for asthma" },
  ],
};
