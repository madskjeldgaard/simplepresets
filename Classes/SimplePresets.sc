// For Nodeproxy and Ndef
SimplePreset {
	var <object, <presets, <presetDir, <presetExtension;

	*new { |object|
		^super.newCopyArgs(object).init();
	}

	init {
		presets = ();
		presetDir = PathName(Platform.userAppSupportDir +/+ "simplepresets");
		presetExtension = ".simplepreset.scd";

		this.checkPresetDir();
		this.updateCurrent();
	}

	names{
		^presets.keys.asArray
	}

	getCurrent{
		^this.objectParams()
	}

	// Update the \current preset with current values of object
	updateCurrent{
		var keysVals = this.objectParams();
		presets.put(\current, keysVals);
	}

	// Save to memory
	saveCurrent{|presetName, overwrite=true|
		var thispreset = this.getCurrent();

		// Default new preset name is date stamp 
		var key = presetName ? "%".format(Date.getDate.stamp); 

		// Check if preset exists
		// And possibly overwrite it
		key = if(presets.at(key).notNil and: overwrite, { 
			"%%".format(key,rand(1000,10000)) // A lousy hack to avoid overwriting
		}, { 
			key 
		});

		key = key.asSymbol;

		"Saving preset %".format(key).postln;

		presets.put(key, thispreset)
	}

	// Load from memory
	loadPresets{|presetName|
		var preset = preset.at(presetName);
		"Presets contain:".postln;
		preset.do{|pair| 
			var param, value;
			# param, value = pair;
			"%: %".format(param, value).postln;

			object.set(param, value)	
		};

		^preset
	}

	// Retrieve preset without loading
	getPreset{|name|
		if(name.isNil, {"No preset name".warn}, {
			if(presets.at(name).isNil, {
				"Can't get preset %. Preset does not exist".format(name).error;
			}, {
				^presets.at(name);
			})
		})
	}

	// Write to disk
	writePresetFile{|filename, presetObject|
		var fn, f;

		// File name
		fn = "%/%%".format(presetDir.fullPath, filename, presetExtension);
		f = File(fn, "w");
		f.write(presetObject.asString);
		f.close
	}

	// Save all presets
	writePresets{
		presets.keysValuesDo{|presetName, preset| 
			this.writePresetFile(presetName, preset)
		};
	}

	// Check integrity and contents of preset dir
	checkPresetDir{
		if(presetDir.isFolder, { 
			// Folder exists
			"Preset folder found at %".format(presetDir.fullPath).postln;
		}, {
			// Folder does not exist

			var result;
			"No preset folder found at %".format(presetDir.fullPath).warn;
			"Attempting to create new preset folder".postln;

			result = File.mkdir(presetDir.fullPath);

			if(result, { 
				"Success".postln
			}, { 
				"Could not create preset directory".error 
			});

		});
	}

	morphEnv{|env, morphStart=0.0, morphEnd=0.5, time=4|
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

		^env
	}

	morphTask{|envelope, blendTo, time=4|
		^Task({
			var env, envval;
			var timegrain = 0.01;

			// Update current preset
			this.getCurrent();

			// Morphing envelope
			env = if(envelope.isNil, {  
				Env(levels:[0,1], times: [time], curve: \lin); 
			}, {  
				this.morphEnv(env: envelope, morphStart:0.0, morphEnd: 1.0, time: time)
			});

			// Convert to pattern
			env = env.asPseg.asStream;
			
			// Initial value of env var
			envval = 0;

			// Iterate through envelope
			while({envval.notNil}, {
				envval = env.next.postln; 
				// This extra control structure is to avoid returning nil
				if(envval.notNil, {
					this.blendParams(object, blend: envval, name1: \current, name2: blendTo);
				});
				timegrain.wait; 
			})
		})
	}

	morph{|envelope, blendTo, time=4| 
		var task = this.morphTask(
			envelope: envelope, 
			blendTo: blendTo, 
			time: time
		);

		^task.play
	}

	// Morph over random envelope
	slouchTowards{|blendTo, time=4|
		var numSegments=8;
		var levels = Array.rand(numSegments, 0.0001, 1.0);
		var times = Array.rand(numSegments, 0.1, 1.0).normalizeSum;
		var curves = Array.rand(numSegments, (-10.0), 10.0);
		var env = Env(levels, times, curves);

		this.morph(envelope: env, blendTo: blendTo, time: time);
	}

	blendParams{|blend=0.5, name1, name2|
		// Source preset
		var thisPreset = if(name1.isNil, { 
			this.getCurrent();
		}, { 
			this.getPreset(name1) 
		});

		// Target preset
		var thatPreset = this.getPreset(name2);

		// Recalculate all parameters using blend function
		// TODO: Make this less ugly
		var blendedParams = thisPreset.collect{|pair|
			var thisParam, value, thatParam, thatValue, blended;
			# thisParam, value = pair;
			# thatValue = thatPreset.select{|pair| pair[0] == thisParam };
			thatValue = thatValue[1]; // Only get value from key-value pair
			blended = value.blend(thatValue, blend);

			"Blending param % from % to %: %".format(thisParam, value, thatValue, blended).postln;

			[thisParam, blended]
		};

		// Set object using new, blended parameters
		blendedParams.do{|pair|
			object.set(*pair)
		};

		^blendedParams
	}

	objectParams{
		// TODO: Add exceptions?
		^object.getKeysValues();
	}

	randAll{|maxRand=0.5|
		this.objectParams.do{|pair| 
			var param, value; 
			#param, value = pair; 
			value = rrand(0.0, maxRand); 
			this.object.set(param, value)
		};

		this.updateCurrent();
	}
}

//TODO
// SimpleNdefPreset{ }
