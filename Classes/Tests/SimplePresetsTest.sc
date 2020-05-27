SimplePresetsTest1 : UnitTest {
	test_check_classname {
		var result = SimplePresets.new;
		this.assert(result.class == SimplePresets);
	}
}


SimplePresetsTester {
	*new {
		^super.new.init();
	}

	init {
		SimplePresetsTest1.run;
	}
}
