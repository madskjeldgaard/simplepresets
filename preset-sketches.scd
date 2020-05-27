/*



Data format for preset
(
	objectName: Ndef(\lalala).key,
	argPairs: [\buffer, 10, \rate, 4.0]
)



*/
(
Ndef(\sine1, {|amp=1, f=0.25, pan=0|
	Pan2.ar(SinOsc.ar(f.linexp(0.0,1.0,40.0,12000.0)) * amp, pan.linlin(0.0,1.0,-1.0,1.0))
});

~presets = ~presets ? ();

~getCurrent = {|ndef, except|
	var keysVals = ndef.getKeysValues(except: except);
	~presets.put(\current, keysVals);
	keysVals
};

~getPreset = {|name|
	if(name.isNil, {"No preset name".warn}, {
		~presets.at(name);
	})
};

// Save preset in memory
~saveCurrent = {|ndef, presetName, overwrite=true|
	var thispreset = ~getCurrent.value(ndef);
	var key = presetName ? "%".format(Date.getDate.stamp); 

	key = if(~presets.at(key).notNil and: overwrite, { "%%".format(key,rand(1000,10000)) }, { key });
	key = key.asSymbol;

	"Saving preset %".format(key).postln;

	~presets.put(key, thispreset);
};

// load preset to nodeproxy 
~loadPresets = {|ndef, presetName|
	var presets = ~presets.at(presetName);
	"Presets contain:".postln;
	presets.do{|pair| 
		var param, value;
		# param, value = pair;
		"%: %".format(param, value).postln;

		ndef.set(param, value)	
	};

	presets
};

~presetDir = PathName(Platform.userAppSupportDir +/+ "simplepresets");
~presetExtension = ".simplepreset.scd";

~writePresetFile = {|filename, presetObject|
	var fn, f;

	// File name
	fn = "%/%%".format(~presetDir.fullPath, filename, ~presetExtension);
	f = File(fn, "w");
	f.write(presetObject.asString);
	f.close
};

~checkPresetDir = {

	if(~presetDir.isFolder, { 
		// Folder exists
		"Preset folder found at %".format(~presetDir.fullPath).postln;

		}, {
		// Folder does not exist

		var result;
		"No preset folder found at %".format(~presetDir.fullPath).warn;
		"Attempting to create new preset folder".postln;

		result = File.mkdir(~presetDir.fullPath);

		if(result, { 
			"Success".postln
		}, { 
			"Could not create preset directory".error 
		});

	});
};

// Save all presets
~writePresets = {
	~presets.keysValuesDo{|presetName, preset| 
		~writePresetFile.value(presetName, preset)
	};
};

~randAll = {|ndef, maxRand=0.5|
	var args = ~getCurrent.(ndef);
	var argPairs;

	args.do{|pair|
		var param, value, newpair;

		#param, value = pair;

		ndef.set(param, rrand(0.0,maxRand));
	};
};

~morphEnv = {|env, morphStart=0.0, morphEnd=0.5, time=4|
	var maximum = env.levels.maxValue({|i| i });
	var minimum = env.levels.minValue({|i| i });

	// Rescale old values to new values
	env.levels = env.levels.linlin(
		minimum,
		maximum,
		morphStart,
		morphEnd
	);

	// Normalize times to match length requested in time arg
	env.times = env.times.normalizeSum * time;

	// Make sure it starts at morph start
	// env.levels[0] = morphStart;

	// And ends at morphEnd
	// env.levels[env.levels.lastIndex] = morphEnd;

	env
};

~morphTask = {|ndef, envelope, blendTo, time=4|
	Task({
		var env, envval;
		var timegrain = 0.01;

		// Update current
		~getCurrent.value(ndef);

		env = if(envelope.isNil, {  
			Env(levels:[0,1], times: [time], curve: \lin); 
		}, {  
			~morphEnv.value(env: envelope, morphStart:0.0, morphEnd: 1.0, time: time)
		});

		env = env.asPseg.asStream;
		envval = 0;

		while({envval.notNil}, {
			envval = env.next.postln; 
			// This extra control structure is to avoid returning nil
			if(envval.notNil, {
				~blendParams.value(ndef, blend: envval, name1: \current, name2: blendTo);
			});
			timegrain.wait; 
		})
	})
};

~morph = {|ndef, envelope, blendTo, time=4| 
	var task = ~morphTask.value(ndef: ndef, envelope: envelope, blendTo: blendTo, time: time);

	task.play;
};

~blendParams = {|ndef, blend=0.5, name1, name2|
	var thisPreset = if(name1.isNil, { 
		~getCurrent.value(ndef, except: nil);
	}, { 
		~getPreset.(name1) 
	});

	var thatPreset = ~getPreset.(name2);

	var blendedParams = thisPreset.collect{|pair|
		var thisParam, value, thatParam, thatValue, blended;
		# thisParam, value = pair;
		# thatValue = thatPreset.select{|pair| pair[0] == thisParam };
		thatValue = thatValue[1]; // Only get value from key-value pair
		blended = value.blend(thatValue, blend);

		"Blending param % from % to %: %".format(thisParam, value, thatValue, blended).postln;

		[thisParam, blended]
	};

	blendedParams.do{|pair|
		ndef.set(*pair)
	};

	blendedParams
};

~xfade={|ndef|};

// Generate some random presets
5.do{|i|
	~getCurrent.(Ndef(\sine1));
	~randAll.(Ndef(\sine1));
	~saveCurrent.(Ndef(\sine1), presetName: "preset%".format(i));
};

Ndef(\sine1).gui;
)

(
~morph.value(
	ndef: Ndef(\sine1), 
	envelope: Env.perc,
	blendTo: ~presets.keys.choose
);
)

(
)


